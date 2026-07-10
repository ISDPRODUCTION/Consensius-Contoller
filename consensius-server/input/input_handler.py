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
    import vgamepad as vg
    VGAMEPAD_AVAILABLE = True
except ImportError:
    VGAMEPAD_AVAILABLE = False

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

        if VGAMEPAD_AVAILABLE:
            try:
                self._gamepad = vg.VX360Gamepad()
            except Exception as e:
                print(f"[WARN] Failed to initialize virtual gamepad: {e}")
                self._gamepad = None
        else:
            self._gamepad = None
            print("[WARN] vgamepad not available. Virtual Xbox 360 controller disabled.")

        # VGamepad button mappings
        if VGAMEPAD_AVAILABLE:
            self._vgamepad_button_map = {
                "a": vg.XUSB_BUTTON.XUSB_GAMEPAD_A,
                "b": vg.XUSB_BUTTON.XUSB_GAMEPAD_B,
                "x": vg.XUSB_BUTTON.XUSB_GAMEPAD_X,
                "y": vg.XUSB_BUTTON.XUSB_GAMEPAD_Y,
                "lb": vg.XUSB_BUTTON.XUSB_GAMEPAD_LEFT_SHOULDER,
                "rb": vg.XUSB_BUTTON.XUSB_GAMEPAD_RIGHT_SHOULDER,
                "lt": "LT", # handled as trigger
                "rt": "RT", # handled as trigger
                "back": vg.XUSB_BUTTON.XUSB_GAMEPAD_BACK,
                "start": vg.XUSB_BUTTON.XUSB_GAMEPAD_START,
                "l3": vg.XUSB_BUTTON.XUSB_GAMEPAD_LEFT_THUMB,
                "r3": vg.XUSB_BUTTON.XUSB_GAMEPAD_RIGHT_THUMB,
                "dpad_up": vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_UP,
                "dpad_down": vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_DOWN,
                "dpad_left": vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_LEFT,
                "dpad_right": vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_RIGHT
            }

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
        Converts left/right joystick XY into vgamepad left/right joystick movements.
        For backwards compatibility or specific profiles, it can also map to WASD, 
        but vgamepad is preferred.
        """
        if stick not in ("left", "right"):
            return

        with self._lock:
            if self._gamepad:
                val_x = float(x)
                # Android UI: Y positif = jari ke atas (sudah dibalik di ControllerScreen: -(rawNy))
                # vgamepad/XInput: Y positif = analog ke atas
                # Jadi tidak perlu invert lagi di sini
                val_y = float(y)

                if stick == "left":
                    self._gamepad.left_joystick_float(x_value_float=val_x, y_value_float=val_y)
                elif stick == "right":
                    self._gamepad.right_joystick_float(x_value_float=val_x, y_value_float=val_y)
                
                self._gamepad.update()
                
            else:
                # Fallback to WASD for left joystick ONLY if gamepad is NOT available
                if stick == "left":
                    threshold = self._settings.get("joystick_threshold", 0.3)
                    new_required: Set[str] = set()
                    if y > threshold:
                        new_required.add("w")
                    if y < -threshold:
                        new_required.add("s")
                    if x > threshold:
                        new_required.add("d")
                    if x < -threshold:
                        new_required.add("a")

                    for key in list(self._pressed_wasd):
                        if key not in new_required:
                            self._raw_release(key)
                            self._pressed_wasd.discard(key)
                            self._pressed_keys.discard(key)

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
            if self._gamepad and key_str.lower() in self._vgamepad_button_map:
                v_btn = self._vgamepad_button_map[key_str.lower()]
                if v_btn == "LT":
                    self._gamepad.left_trigger(value=255 if is_down else 0)
                elif v_btn == "RT":
                    self._gamepad.right_trigger(value=255 if is_down else 0)
                else:
                    if is_down:
                        self._gamepad.press_button(button=v_btn)
                    else:
                        self._gamepad.release_button(button=v_btn)
                self._gamepad.update()
            else:
                if is_down:
                    self._press_key(key_str)
                else:
                    self._release_key(key_str)

    def process_dpad(self, key_str: str, state: str) -> None:
        """D-Pad is treated identically to a button."""
        self.process_button(key_str, state)

    # ── Skill + Aim ───────────────────────────────────────────────────────────

    def process_skill_aim(self, action: str, angle: float, magnitude: float, state: str = "cast",
                          aim_stick: str = "mouse", extra_keys: list = None) -> None:
        """
        Skill aim dengan 3 mode:
          - aim_stick="mouse"  → gerak mouse + tekan key keyboard (mode lama)
          - aim_stick="right"  → tahan tombol gamepad + gerak right stick vgamepad
          - aim_stick="left"   → tahan tombol gamepad + gerak left stick vgamepad

        extra_keys: tombol tambahan yang ditekan bersamaan (misal ["x"] untuk RB+X)
        """
        if extra_keys is None:
            extra_keys = []

        scaled_magnitude = magnitude ** 0.7 if magnitude > 0 else 0.0
        angle_rad = math.radians(angle)

        # ── MODE GAMEPAD STICK (aimStick = "right" atau "left") ─────────────
        if aim_stick in ("right", "left") and self._gamepad:
            with self._lock:
                v_btn = self._vgamepad_button_map.get(action.lower()) if action else None

                if state == "aiming":
                    # Tekan tombol utama (skillKey) jika belum ditekan
                    if action not in self._held_skill_keys:
                        self._press_gamepad_button(v_btn)
                        # Tekan extra keys
                        for ek in extra_keys:
                            ek_btn = self._vgamepad_button_map.get(ek.lower())
                            self._press_gamepad_button(ek_btn)
                        self._held_skill_keys[action] = action
                        self._skill_aim_active.add(action)

                    # Gerak stick sesuai angle dan magnitude
                    rx = float(math.cos(angle_rad) * scaled_magnitude)
                    ry = float(-math.sin(angle_rad) * scaled_magnitude)
                    if aim_stick == "right":
                        self._gamepad.right_joystick_float(x_value_float=rx, y_value_float=ry)
                    else:
                        self._gamepad.left_joystick_float(x_value_float=rx, y_value_float=ry)
                    self._gamepad.update()

                elif state == "cast":
                    self._skill_aim_active.discard(action)
                    self._held_skill_keys.pop(action, None)
                    # Lepas tombol utama
                    self._release_gamepad_button(v_btn)
                    # Lepas extra keys
                    for ek in extra_keys:
                        ek_btn = self._vgamepad_button_map.get(ek.lower())
                        self._release_gamepad_button(ek_btn)
                    # Reset stick
                    if aim_stick == "right":
                        self._gamepad.right_joystick_float(x_value_float=0.0, y_value_float=0.0)
                    else:
                        self._gamepad.left_joystick_float(x_value_float=0.0, y_value_float=0.0)
                    self._gamepad.update()
            return

        # ── MODE MOUSE (aimStick = "mouse") ─────────────────────────────────
        if not PYNPUT_AVAILABLE or self._keyboard is None:
            return
        key = _resolve_key(action)
        if key is None:
            return

        max_px = float(self._settings.get("skill_aim_distance", 300))
        max_px = max(max_px, 50.0)
        aim_dx = int(math.cos(angle_rad) * scaled_magnitude * max_px)
        aim_dy = int(math.sin(angle_rad) * scaled_magnitude * max_px)

        with self._lock:
            if state == "aiming":
                # Deteksi sesi stale (key masih tertahan dari sesi sebelumnya)
                if action in self._held_skill_keys and action not in self._skill_aim_active:
                    stale_key = self._held_skill_keys.pop(action)
                    try:
                        self._keyboard.release(stale_key)
                    except Exception:
                        pass
                    self._skill_aim_origin.pop(action, None)
                    self._skill_aim_center.pop(action, None)

                # Setup awal sesi baru
                if action not in self._skill_aim_active:
                    self._skill_aim_active.add(action)
                    if self._mouse:
                        try:
                            self._skill_aim_origin[action] = self._mouse.position
                        except Exception:
                            self._skill_aim_origin[action] = (0, 0)
                    skill_positions = self._settings.get("skill_positions", {})
                    pos_cfg = skill_positions.get(action, {})
                    px = pos_cfg.get("x", -1) if isinstance(pos_cfg, dict) else -1
                    py = pos_cfg.get("y", -1) if isinstance(pos_cfg, dict) else -1
                    if px >= 0 and py >= 0:
                        self._skill_aim_center[action] = (int(px), int(py))
                    else:
                        sw, sh = self._screen_size if self._screen_size[0] > 0 else (1920, 1080)
                        self._skill_aim_center[action] = (sw // 2, sh // 2)

                # Gerak cursor ke posisi aim SEBELUM tekan key
                center = self._skill_aim_center.get(action)
                if self._mouse and center:
                    try:
                        self._mouse.position = (center[0] + aim_dx, center[1] + aim_dy)
                    except Exception:
                        pass

                # Tekan key (cursor sudah di posisi yang benar)
                if action not in self._held_skill_keys:
                    try:
                        self._keyboard.press(key)
                        self._held_skill_keys[action] = key
                    except Exception:
                        pass

            elif state == "cast":
                self._skill_aim_active.discard(action)
                center   = self._skill_aim_center.pop(action, None)
                origin   = self._skill_aim_origin.pop(action, None)
                held_key = self._held_skill_keys.pop(action, None)

                # Gerak cursor ke posisi final
                if self._mouse and center:
                    try:
                        self._mouse.position = (center[0] + aim_dx, center[1] + aim_dy)
                    except Exception:
                        pass

                # Lepas key
                if held_key is not None:
                    try:
                        self._keyboard.release(held_key)
                    except Exception:
                        pass
                else:
                    try:
                        self._keyboard.press(key)
                        self._keyboard.release(key)
                    except Exception:
                        pass

                # Kembalikan cursor ke posisi semula
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
            # Release gamepad buttons
            if self._gamepad:
                self._gamepad.reset()
                self._gamepad.update()
                
            for key_str in list(self._pressed_keys):
                self._raw_release(key_str)
            self._pressed_keys.clear()
            self._pressed_wasd.clear()

            # Bug SKILL-HOLD: Release any skill keys that are still held on disconnect.
            for action, held_key in list(self._held_skill_keys.items()):
                try:
                    if self._gamepad and action.lower() in self._vgamepad_button_map:
                        pass # handled by gamepad.reset()
                    elif self._keyboard:
                        self._keyboard.release(held_key)
                        # Fix #5: removed print() ?" not needed at disconnect
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

    def _press_gamepad_button(self, v_btn) -> None:
        """Tekan satu tombol vgamepad. Caller harus pegang lock."""
        if v_btn is None or self._gamepad is None:
            return
        try:
            if v_btn == "LT":
                self._gamepad.left_trigger(value=255)
            elif v_btn == "RT":
                self._gamepad.right_trigger(value=255)
            else:
                self._gamepad.press_button(button=v_btn)
        except Exception:
            pass

    def _release_gamepad_button(self, v_btn) -> None:
        """Lepas satu tombol vgamepad. Caller harus pegang lock."""
        if v_btn is None or self._gamepad is None:
            return
        try:
            if v_btn == "LT":
                self._gamepad.left_trigger(value=0)
            elif v_btn == "RT":
                self._gamepad.right_trigger(value=0)
            else:
                self._gamepad.release_button(button=v_btn)
        except Exception:
            pass

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
