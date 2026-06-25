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
        """
        is_down = state.lower() == "down"
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
        Bug SKILL-HOLD + SKILL-CURSOR fix:

          On first "aiming" message:
            1. Press-and-hold the skill key.
            2. Save the current mouse position as the "origin" to restore later.
            3. Compute screen center as the aim pivot (so cursor starts at character).
            4. Move cursor to screenCenter + aimOffset (absolute position).

          On subsequent "aiming" messages:
            - Update cursor to screenCenter + aimOffset (absolute) so it tracks joystick.

          On "cast" (finger released):
            1. Move cursor to final aim position.
            2. Release the held skill key.
            3. Restore cursor to the saved origin position.

        This fixes the cursor-in-top-left problem: instead of small relative moves
        that cancel out (move +dx, -dx = no net movement), we place the cursor
        absolutely from the screen center where the skill targeting reticle starts.
        """
        if not PYNPUT_AVAILABLE or self._keyboard is None:
            return
        key = _resolve_key(action)
        if key is None:
            return

        # skill_aim_distance is treated as a PERCENTAGE (0–100) of the screen WIDTH.
        # Default 25 → at magnitude=1.0 the cursor moves screen_width * 0.25 pixels.
        # On a 1920-wide display: 25% = 480 px, covering most of the targeting range.
        # (Old small values like 80 are used as-is and will be ≈ 80% of screen width
        #  if the user hasn't updated their settings — warn them if > 100.)
        sad_pct     = self._settings.get("skill_aim_distance", 25)
        # Get or cache screen width
        if self._screen_size[0] < 0:
            try:
                sw = ctypes.windll.user32.GetSystemMetrics(0)
                sh = ctypes.windll.user32.GetSystemMetrics(1)
                self._screen_size = (sw, sh)
            except Exception:
                self._screen_size = (1920, 1080)
        screen_w = self._screen_size[0]
        # Convert % to pixels (clamp to something reasonable)
        max_px = int(screen_w * (min(float(sad_pct), 100.0) / 100.0))
        max_px = max(max_px, 60)  # at least 60 px

        angle_rad = math.radians(angle)
        aim_dx = int(math.cos(angle_rad) * magnitude * max_px)
        aim_dy = int(math.sin(angle_rad) * magnitude * max_px)

        with self._lock:
            if state == "aiming":
                # ── First aiming message: initialise aim session ─────────────
                if action not in self._held_skill_keys:
                    # 1. Press-and-hold skill key
                    try:
                        self._keyboard.press(key)
                        self._held_skill_keys[action] = key
                        print(f"[SKILL_HOLD] PRESS action={action!r} key={key!r}")
                    except Exception:
                        pass

                    # 2. Save current cursor position (restore on cast)
                    if self._mouse:
                        try:
                            origin = self._mouse.position  # (x, y) tuple
                            self._skill_aim_origin[action] = origin
                            print(f"[SKILL_CURSOR] saved origin={origin}")
                        except Exception:
                            self._skill_aim_origin[action] = (0, 0)

                    # 3. Determine aim pivot = configured skill button position
                    #    or screen center as fallback
                    skill_positions = self._settings.get("skill_positions", {})
                    pos_cfg = skill_positions.get(action, {})
                    px = pos_cfg.get("x", -1) if isinstance(pos_cfg, dict) else -1
                    py = pos_cfg.get("y", -1) if isinstance(pos_cfg, dict) else -1

                    if px >= 0 and py >= 0:
                        # Use the configured skill button position on screen
                        self._skill_aim_center[action] = (int(px), int(py))
                        print(f"[SKILL_CURSOR] using skill pos ({px}, {py}) for action={action!r}")
                    else:
                        # Fallback: screen center (position not configured yet)
                        sw, sh = self._screen_size if self._screen_size[0] > 0 else (1920, 1080)
                        self._skill_aim_center[action] = (sw // 2, sh // 2)
                        print(f"[SKILL_CURSOR] no pos cfg for {action!r}, using screen center ({sw//2}, {sh//2})")

                # 4. Move cursor to center + aim offset (absolute positioning)
                center = self._skill_aim_center.get(action)
                if self._mouse and center:
                    target_x = center[0] + aim_dx
                    target_y = center[1] + aim_dy
                    try:
                        self._mouse.position = (target_x, target_y)
                    except Exception:
                        pass

            elif state == "cast":
                # ── Finger released: move to final aim, release key, restore cursor ──
                center = self._skill_aim_center.get(action)
                if self._mouse and center:
                    target_x = center[0] + aim_dx
                    target_y = center[1] + aim_dy
                    try:
                        self._mouse.position = (target_x, target_y)
                    except Exception:
                        pass

                # Release the held skill key
                held_key = self._held_skill_keys.pop(action, None)
                if held_key is not None:
                    try:
                        self._keyboard.release(held_key)
                        print(f"[SKILL_HOLD] RELEASE action={action!r} key={held_key!r}")
                    except Exception:
                        pass
                else:
                    # Fallback: key wasn't held, do a quick tap
                    try:
                        self._keyboard.press(key)
                        self._keyboard.release(key)
                        print(f"[SKILL_HOLD] TAP (fallback) action={action!r} key={key!r}")
                    except Exception:
                        pass

                # Restore cursor to the position it was before skill aim started
                origin = self._skill_aim_origin.pop(action, None)
                self._skill_aim_center.pop(action, None)
                if self._mouse and origin:
                    try:
                        self._mouse.position = origin
                        print(f"[SKILL_CURSOR] restored to origin={origin}")
                    except Exception:
                        pass

    # ── Mouse click (touchpad tap) ────────────────────────────────────────────

    def process_mouse_click(self, button: str, state: str) -> None:
        """
        SYNC FIX: Android sends type="mouse_click" {button, state}.
        Handles left/right button press and release via pynput.
          - 1 tap  → left click down + up
          - 2 taps → right click down + up (sent by Android as two-finger tap)
          - hold   → left down (drag), then left up on release
        """
        if not PYNPUT_AVAILABLE or self._mouse is None:
            return
        try:
            from pynput.mouse import Button as MouseButton
            btn = MouseButton.left if button == "left" else MouseButton.right
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
                        print(f"[SKILL_HOLD] FORCE RELEASE on disconnect action={action!r}")
                except Exception:
                    pass
            self._held_skill_keys.clear()

            # Bug SKILL-CURSOR: Restore cursor to origin position if skill aim was active.
            for action, origin in list(self._skill_aim_origin.items()):
                try:
                    if self._mouse and origin:
                        self._mouse.position = origin
                        print(f"[SKILL_CURSOR] RESTORE on disconnect action={action!r} origin={origin}")
                except Exception:
                    pass
            self._skill_aim_origin.clear()
            self._skill_aim_center.clear()

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
