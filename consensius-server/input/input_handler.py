"""
input/input_handler.py
======================
Handles all actual keyboard and mouse input via pynput.
Called from the WebSocket message handler (background thread-safe).

Bugs fixed:
  - Bug 2:  pressed_keys = set() prevents duplicate press/release events.
  - Bug 10: pressed_wasd = set() tracks WASD independently; on every joystick
            message the full new required set is computed, then stale keys are
            released and new keys are pressed — guaranteeing diagonal release.
  - Bug JOYSTICK-MOVE: Removed double Y-invert. Android already sends game-space
            Y (positive = forward/up). invert_y flag was inverting AGAIN causing
            W/S keys to never register. Now Y is used as-is from Android.
  - Bug SKILL-HOLD: Skill aim joystick now holds the key on first aiming message
            and releases it on cast (finger release), matching expected behavior
            where press-and-hold shows the skill, release fires it.
"""
import math
import threading
import ctypes
from typing import Set

try:
    from pynput.keyboard import Controller as KeyboardController, Key
    from pynput.mouse import Controller as MouseController
    PYNPUT_AVAILABLE = True
except ImportError:
    PYNPUT_AVAILABLE = False


# ── Key name → pynput Key or str mapping ─────────────────────────────────────
_SPECIAL_KEYS: dict = {}

if PYNPUT_AVAILABLE:
    _SPECIAL_KEYS = {
        "space":     Key.space,
        "enter":     Key.enter,
        "shift":     Key.shift,
        "ctrl":      Key.ctrl,
        "alt":       Key.alt,
        "tab":       Key.tab,
        "backspace": Key.backspace,
        "escape":    Key.esc,
        "esc":       Key.esc,
        "up":        Key.up,
        "down":      Key.down,
        "left":      Key.left,
        "right":     Key.right,
        "f1": Key.f1,  "f2": Key.f2,  "f3": Key.f3,  "f4": Key.f4,
        "f5": Key.f5,  "f6": Key.f6,  "f7": Key.f7,  "f8": Key.f8,
        "f9": Key.f9, "f10": Key.f10, "f11": Key.f11, "f12": Key.f12,
    }


def _resolve_key(key_str: str):
    """Resolves a key string to a pynput-compatible key."""
    if not PYNPUT_AVAILABLE:
        return None
    k = key_str.lower().strip()
    return _SPECIAL_KEYS.get(k, k[0] if len(k) == 1 else None)


class InputHandler:
    """
    Thread-safe keyboard and mouse input executor.
    Uses pynput to send real system-level keypresses and mouse moves.
    """

    def __init__(self, settings: dict):
        self._lock = threading.Lock()
        self._settings = settings

        if PYNPUT_AVAILABLE:
            self._keyboard = KeyboardController()
            self._mouse    = MouseController()
        else:
            self._keyboard = None
            self._mouse    = None

        # Bug 2: Use set() to track held keys — prevents duplicate press/release.
        # press_key() only calls pynput if key NOT already in set.
        # release_key() only calls pynput if key IS in set.
        self._pressed_keys: Set[str] = set()

        # Bug 10: Separate set for WASD to allow precise diagonal release.
        # On every joystick message, compute full new required set and diff.
        self._pressed_wasd: Set[str] = set()

        # Bug SKILL-HOLD: Track currently held skill keys (action -> key_str).
        # Key is pressed on first "aiming" message, released on "cast".
        self._held_skill_keys: dict = {}  # action_str -> key_str

        # Bug SKILL-CURSOR: Track per-action aim state for absolute cursor positioning.
        self._skill_aim_origin: dict = {}  # action_str -> (origin_x, origin_y)
        self._skill_aim_center: dict = {}  # action_str -> (center_x, center_y)

        # ── BUG AIM-SESSION: Track skill aim sessions to distinguish between ──
        # stale sessions (key held from aborted previous session) vs ongoing
        # sessions (currently being dragged, sending "aiming" every frame).
        # Without this, EVERY "aiming" message after the first would trigger
        # the stale handler, causing the key to be released/pressed 60fps and
        # the origin to be overwritten with the already-moved cursor position.
        self._skill_aim_active: set = set()  # set of action_str with active session

        # Cache screen size so we don't call GetSystemMetrics on every aiming frame.
        self._screen_size: tuple = (-1, -1)  # (width, height), fetched lazily

    def update_settings(self, settings: dict) -> None:
        with self._lock:
            self._settings = settings

    # ── Joystick → WASD ───────────────────────────────────────────────────────

    def process_joystick(self, stick: str, x: float, y: float) -> None:
        """
        Converts left joystick XY into WASD keypresses.

        Bug JOYSTICK-MOVE fix:
          Android already sends game-space Y:
            ny = -(joystickOffY / maxR)  → positive Y means joystick pushed UP (forward)
          So DO NOT invert Y here — that would cause a double-invert and
          W/S would never trigger. Use Y directly as received from Android.

        Bug 10 fix: Computes the FULL new required set of WASD keys, then:
          - Releases any key in pressed_wasd NOT in new_required
          - Presses any key in new_required NOT already in pressed_wasd
        This guarantees diagonal keys are released when moving back to single axis.
        """
        if stick != "left":
            return

        threshold = self._settings.get("joystick_threshold", 0.3)

        # Bug JOYSTICK-MOVE FIX: Do NOT invert Y.
        # Android sends: ny = -(joystickOffY / maxR) — already in game-space
        # where positive Y = forward (up) and negative Y = backward (down).
        # Inverting here again would make W/S never register.
        # (The old invert_y flag was designed for a different Android convention.)

        # Compute the full set of keys that should be pressed right now
        new_required: Set[str] = set()
        if y > threshold:
            new_required.add("w")   # positive Y = forward
        if y < -threshold:
            new_required.add("s")   # negative Y = backward
        if x > threshold:
            new_required.add("d")   # positive X = right
        if x < -threshold:
            new_required.add("a")   # negative X = left

        with self._lock:
            # Release any WASD key no longer needed
            for key in list(self._pressed_wasd):
                if key not in new_required:
                    self._raw_release(key)
                    self._pressed_wasd.discard(key)
                    self._pressed_keys.discard(key)

            # Press any newly required key not yet held
            for key in new_required:
                if key not in self._pressed_wasd:
                    self._raw_press(key)
                    self._pressed_wasd.add(key)
                    self._pressed_keys.add(key)

    # ── Mouse movement ────────────────────────────────────────────────────────

    def process_mouse(self, dx: float, dy: float) -> None:
        """
        Bug 6 (already fixed): Multiplies dx/dy by mouse_sensitivity before
        passing to pynput mouse controller.
        """
        if not PYNPUT_AVAILABLE or self._mouse is None:
            return
        sens = self._settings.get("mouse_sensitivity", 1.5)
        with self._lock:
            self._mouse.move(int(dx * sens), int(dy * sens))

    # ── Button / D-Pad ────────────────────────────────────────────────────────

    def process_button(self, key_str: str, state: str) -> None:
        """
        Bug 2 fix: Uses pressed_keys set() to deduplicate.
        press_key() only fires pynput if key not already held.
        release_key() only fires pynput if key is currently held.

        BUG MOUSE-FIX: Handle mouse keys ("mouse1", "mouse2", "left_click",
        "right_click", "middle_click") that come from the Android app's
        button elements. Previously these were sent to _press_key/_release_key
        which tried to press them as keyboard keys and silently ignored them.
        """
        is_down = state.lower() == "down"

        # ── Check for mouse button keys (BEFORE lock to avoid deadlock) ─
        # process_mouse_click acquires self._lock internally, so we must
        # NOT call it while already holding the lock (threading.Lock is
        # not reentrant).
        mouse_map = {
            "mouse1":       "left",
            "mouse2":       "right",
            "left_click":   "left",
            "right_click":  "right",
            "middle_click": "middle",
        }
        mouse_button = mouse_map.get(key_str.lower().strip())
        if mouse_button is not None:
            self.process_mouse_click(mouse_button, state)
            return

        # ── Regular keyboard key ────────────────────────────────────────
        with self._lock:
            if is_down:
                self._press_key(key_str)
            else:
                self._release_key(key_str)

    def process_dpad(self, key_str: str, state: str) -> None:
        """D-Pad is treated identically to a button."""
        self.process_button(key_str, state)

    # ── Skill + Aim ───────────────────────────────────────────────────────────

    def process_skill_aim(self, action: str, angle: float, magnitude: float, state: str = "cast") -> None:
        """
        Skill aim: press-and-hold key on first "aiming", track cursor, release on "cast".

        BUG-STUCK FIX (2 root causes fixed):

        Root Cause #1 — Android skips sending "cast" when drag magnitude < 0.1
          The Android code has:  if (mag >= 0.1f) { sendSkillAim(..., "cast") }
          So a light tap on the skill joystick sends "aiming" (key pressed) but
          NEVER sends "cast" (key never released) → key stuck held indefinitely.

        Root Cause #2 — Stale _held_skill_keys from a previous aborted session
          If cast was never received, _held_skill_keys[action] still exists on the
          next session. The old code then skips the press entirely (action already
          in dict) → key never pressed → skill does nothing → user presses again.

        Fix:
          - On "aiming" when action is ALREADY in _held_skill_keys: force-close the
            stale session (release old key, restore cursor) then start a fresh one.
          - On "cast": always pop ALL state dicts for the action before doing anything,
            guaranteeing no orphaned state survives regardless of exceptions.
        """
        if not PYNPUT_AVAILABLE or self._keyboard is None:
            return
        key = _resolve_key(action)
        if key is None:
            return

        max_px = float(self._settings.get("skill_aim_distance", 300))
        max_px = max(max_px, 50.0)
        scaled_magnitude = magnitude ** 0.7 if magnitude > 0 else 0.0
        angle_rad = math.radians(angle)
        aim_dx = int(math.cos(angle_rad) * scaled_magnitude * max_px)
        aim_dy = int(math.sin(angle_rad) * scaled_magnitude * max_px)

        with self._lock:
            if state == "aiming":

                # ── BUG AIM-SESSION: Detect TRUE stale sessions only ─────────
                # Old code checked `if action in self._held_skill_keys`, but this
                # fires on EVERY "aiming" frame (60fps) because the key was pressed
                # in the first frame and stays in _held_skill_keys throughout the
                # drag. This caused:
                #   1. Key release/press 60 times/second
                #   2. Origin overwritten with cursor's already-moved position
                #   3. Center constantly reset → aim stutter
                #
                # Fix: A stale session = key held AND NOT in _skill_aim_active.
                # Once a session starts, _skill_aim_active is set and cleared on
                # "cast", so only truly orphaned sessions trigger the stale path.
                if action in self._held_skill_keys and action not in self._skill_aim_active:
                    # ── BUG-STUCK FIX Root Cause #2: stale session still open ──
                    # A prior "cast" was never received (e.g. Android mag < 0.1
                    # threshold suppressed it). Force-close the orphaned session first
                    # so the new press always works correctly.
                    stale_key = self._held_skill_keys.pop(action)
                    try:
                        self._keyboard.release(stale_key)
                    except Exception:
                        pass
                    # Pop stale state silently — discard to prevent cursor jump.
                    self._skill_aim_origin.pop(action, None)
                    self._skill_aim_center.pop(action, None)

                # ── First-time setup: save initial state ────────────────────
                if action not in self._skill_aim_active:
                    self._skill_aim_active.add(action)

                    # Save current cursor position BEFORE moving (restored on cast)
                    if self._mouse:
                        try:
                            origin_pos = self._mouse.position
                            self._skill_aim_origin[action] = origin_pos
                        except Exception:
                            self._skill_aim_origin[action] = (0, 0)

                    # Determine aim pivot (configured position or screen centre)
                    skill_positions = self._settings.get("skill_positions", {})
                    pos_cfg = skill_positions.get(action, {})
                    px = pos_cfg.get("x", -1) if isinstance(pos_cfg, dict) else -1
                    py = pos_cfg.get("y", -1) if isinstance(pos_cfg, dict) else -1
                    if px >= 0 and py >= 0:
                        self._skill_aim_center[action] = (int(px), int(py))
                    else:
                        sw, sh = self._screen_size if self._screen_size[0] > 0 else (1920, 1080)
                        self._skill_aim_center[action] = (sw // 2, sh // 2)

                # ═══ BUG ORDER-FIX: Move cursor FIRST, then press key ═══
                # CRITICAL: The game reads the cursor position WHEN the key
                # is pressed (key down event), not continuously. If we press
                # the key FIRST and then move the cursor, the game sees the
                # OLD cursor position (e.g. center of screen), not the aim
                # position. This makes the skill fire in the wrong direction.
                #
                # Fix: Always move cursor to the aim position BEFORE pressing
                # the key. On the initial frame, cursor moves → then key
                # presses. On subsequent frames, cursor keeps updating.
                center = self._skill_aim_center.get(action)
                if self._mouse and center:
                    try:
                        self._mouse.position = (center[0] + aim_dx, center[1] + aim_dy)
                    except Exception:
                        pass

                # ── Press key (cursor is ALREADY at aim position now) ─────
                if action not in self._held_skill_keys:
                    try:
                        self._keyboard.press(key)
                        self._held_skill_keys[action] = key
                    except Exception:
                        pass

            elif state == "cast":
                # Mark session as ended BEFORE popping state
                self._skill_aim_active.discard(action)

                # ── BUG-STUCK FIX Root Cause #1: always pop ALL state first ──
                # Pop everything before acting so no orphaned state survives even
                # if an exception occurs mid-cast (e.g. pynput error).
                center   = self._skill_aim_center.pop(action, None)
                origin   = self._skill_aim_origin.pop(action, None)
                held_key = self._held_skill_keys.pop(action, None)

                # 1. Move cursor to final aimed position
                if self._mouse and center:
                    try:
                        self._mouse.position = (center[0] + aim_dx, center[1] + aim_dy)
                    except Exception:
                        pass

                # 2. Release held key (or tap if session was somehow skipped)
                if held_key is not None:
                    try:
                        self._keyboard.release(held_key)
                    except Exception:
                        pass
                else:
                    # Fallback: no held key — quick tap at current position
                    try:
                        self._keyboard.press(key)
                        self._keyboard.release(key)
                    except Exception:
                        pass

                # ── BUG CURSOR-FIX: Restore cursor to origin ─────────────────
                # Old code had a double-pop bug: origin was popped at the top
                # and then popped AGAIN here (returning None), so cursor was
                # NEVER restored. Fixed by using the origin from the first pop.
                # This ensures cursor returns to the position BEFORE aiming,
                # so subsequent clicks (e.g. fire button via touchpad) happen
                # at the correct pre-aim location.
                #
                # Note: _skill_aim_center was already popped above — no need
                # to pop again.
                if self._mouse and origin is not None:
                    try:
                        self._mouse.position = origin
                    except Exception:
                        pass

    # ── Mouse click (touchpad tap) ────────────────────────────────────────────

    def process_mouse_click(self, button: str, state: str) -> None:
        """
        SYNC FIX: Android sends type="mouse_click" {button, state}.
        Handles left/right/middle button press and release via pynput.
          - 1 tap  → left click down + up
          - 2 taps → right click down + up (sent by Android as two-finger tap)
          - hold   → left down (drag), then left up on release
        """
        if not PYNPUT_AVAILABLE or self._mouse is None:
            return
        try:
            from pynput.mouse import Button as MouseButton
            btn_map = {"left": MouseButton.left, "right": MouseButton.right, "middle": MouseButton.middle}
            btn = btn_map.get(button, MouseButton.left)
            with self._lock:
                if state == "down":
                    self._mouse.press(btn)
                elif state == "up":
                    self._mouse.release(btn)
        except Exception:
            pass

    # ── Mouse scroll (2-finger drag on touchpad) ──────────────────────────────

    def process_mouse_scroll(self, dy: float) -> None:
        """
        SYNC FIX: Android sends type="mouse_scroll" {dy}.
        Positive dy = scroll up, negative dy = scroll down.
        """
        if not PYNPUT_AVAILABLE or self._mouse is None:
            return
        with self._lock:
            # pynput scroll: positive = scroll up
            self._mouse.scroll(0, dy)

    # ── Release all (called on disconnect) ────────────────────────────────────

    def release_all(self) -> None:
        """
        Bug 1 fix: Releases ALL currently held keys.
        Called on both clean disconnect and unexpected connection drop.
        Iterates pressed_keys set and releases each via pynput.
        Also clears pressed_wasd set and held skill keys.
        """
        with self._lock:
            for key_str in list(self._pressed_keys):
                self._raw_release(key_str)
            self._pressed_keys.clear()
            self._pressed_wasd.clear()

            # Bug SKILL-HOLD: Release any skill keys that are still held on disconnect.
            for action, held_key in list(self._held_skill_keys.items()):
                try:
                    if self._keyboard:
                        self._keyboard.release(held_key)
                        # Fix #5: removed print() — not needed at disconnect
                except Exception:
                    pass
            self._held_skill_keys.clear()

            # Bug SKILL-CURSOR: Restore cursor to origin position if skill aim was active.
            for action, origin in list(self._skill_aim_origin.items()):
                try:
                    if self._mouse and origin:
                        self._mouse.position = origin
                        # Fix #5: removed print()
                except Exception:
                    pass
            self._skill_aim_origin.clear()
            self._skill_aim_center.clear()
            self._skill_aim_active.clear()

            # Note: _held_skill_keys is also cleared above in the SKILL-HOLD block.

    # ── Internal helpers ──────────────────────────────────────────────────────

    def _press_key(self, key_str: str) -> None:
        """
        Bug 2 fix: Only calls pynput press if key not already in pressed_keys.
        Caller must hold self._lock.
        """
        if key_str in self._pressed_keys:
            return  # Already pressed — deduplicate
        self._raw_press(key_str)
        self._pressed_keys.add(key_str)

    def _release_key(self, key_str: str) -> None:
        """
        Bug 2 fix: Only calls pynput release if key is in pressed_keys.
        Caller must hold self._lock.
        """
        if key_str not in self._pressed_keys:
            return  # Not held — nothing to release
        self._raw_release(key_str)
        self._pressed_keys.discard(key_str)

    def _raw_press(self, key_str: str) -> None:
        """Calls pynput press without any state check. Caller must hold lock."""
        if not PYNPUT_AVAILABLE or self._keyboard is None:
            return
        key = _resolve_key(key_str)
        if key is None:
            return
        try:
            self._keyboard.press(key)
        except Exception:
            pass

    def _raw_release(self, key_str: str) -> None:
        """Calls pynput release without any state check. Caller must hold lock."""
        if not PYNPUT_AVAILABLE or self._keyboard is None:
            return
        key = _resolve_key(key_str)
        if key is None:
            return
        try:
            self._keyboard.release(key)
        except Exception:
            pass
