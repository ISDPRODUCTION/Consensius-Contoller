import asyncio
import json
import math
import time
import websockets
from pynput.keyboard import Controller as KController, Key
from pynput.mouse import Controller as MController, Button

# ── Controllers (instantiated once at module level) ──────────────────────────
keyboard = KController()
mouse    = MController()

# ── Server port ──────────────────────────────────────────────────────────────
PORT = 8765

# ── Special key string → pynput Key enum mapping ─────────────────────────────
SPECIAL_KEY_MAP = {
    'space': Key.space,
    'shift': Key.shift,
    'ctrl':  Key.ctrl,
    'alt':   Key.alt,
    'enter': Key.enter,
    'tab':   Key.tab,
    'esc':   Key.esc,
    'backspace': Key.backspace,
    'delete': Key.delete,
    'up':    Key.up,
    'down':  Key.down,
    'left':  Key.left,
    'right': Key.right,
}

def resolve_key(key_str: str):
    """Return a pynput-compatible key from a string."""
    if not key_str:
        return None
    if key_str in SPECIAL_KEY_MAP:
        return SPECIAL_KEY_MAP[key_str]
    # Single character → press directly as char
    return key_str[0]

# ── Button/skill action name → keyboard key string ────────────────────────────
# The Android app may send either the action name (e.g. "skill1") OR directly
# the key string (e.g. "k") in the "action" / "key" fields.
BUTTON_MAP = {
    "skill1": "j",
    "skill2": "k",
    "skill3": "l",
    "attack": "space",
    "spell":  "i",
    "recall": "b",
    "regen":  "h",
}

def resolve_action_key(action: str) -> str:
    """
    Accepts either an action name ('skill1', 'attack', …) or a raw key string
    ('k', 'space', …) and returns the final key string to press.
    """
    # If the action is a known action name, look it up
    if action in BUTTON_MAP:
        return BUTTON_MAP[action]
    # Otherwise treat it directly as a key string (already resolved)
    return action

# ── WASD state ────────────────────────────────────────────────────────────────
pressed_wasd: set = set()        # currently held movement keys ('w','a','s','d')
prev_joystick_x: float = None
prev_joystick_y: float = None

# ── Skill-aim cooldown tracker: action → last_cast_time ──────────────────────
skill_last_cast: dict = {}
SKILL_CAST_COOLDOWN = 0.3        # seconds

# ── Skill-aim delta tracker: action → (last_dx, last_dy) ─────────────────────
# Tracks the LAST mouse offset applied while aiming so we only move by the
# difference on each frame instead of the full value (prevents cursor drift).
skill_aim_last: dict = {}        # action → (last_dx, last_dy)

# ── Skill-aim mouse travel at full magnitude (pixels) ────────────────────────
SKILL_AIM_DISTANCE = 200.0       # increased for better aiming range

# ── Right-joystick aim multiplier ────────────────────────────────────────────
AIM_MULTIPLIER = 15.0

# ─────────────────────────────────────────────────────────────────────────────

def press_key(key_str: str):
    key = resolve_key(key_str)
    if key is None:
        print(f"[KEY PRESS] WARNING: empty key_str, skipping")
        return
    print(f"[KEY PRESS]   '{key_str}' → {key!r}")
    keyboard.press(key)

def release_key(key_str: str):
    key = resolve_key(key_str)
    if key is None:
        print(f"[KEY RELEASE] WARNING: empty key_str, skipping")
        return
    print(f"[KEY RELEASE] '{key_str}' → {key!r}")
    keyboard.release(key)

# ── Movement joystick → WASD ──────────────────────────────────────────────────
def handle_joystick_movement(x: float, y: float):
    global pressed_wasd, prev_joystick_x, prev_joystick_y

    # Skip if values haven't meaningfully changed (reduces noise)
    if prev_joystick_x is not None and prev_joystick_y is not None:
        if abs(x - prev_joystick_x) < 0.01 and abs(y - prev_joystick_y) < 0.01:
            return

    prev_joystick_x = x
    prev_joystick_y = y

    threshold = 0.3
    new_keys: set = set()

    # Android normalises: +y = forward/up, -y = back/down
    # Screen Y is already inverted by Android before sending (ny = -clampedY/maxR)
    if y > threshold:  new_keys.add('w')
    if y < -threshold: new_keys.add('s')
    if x > threshold:  new_keys.add('d')
    if x < -threshold: new_keys.add('a')

    print(f"[JOYSTICK MOVE] x={x:.3f} y={y:.3f} → target={new_keys} held={pressed_wasd}")

    for key in (pressed_wasd - new_keys):
        release_key(key)
    for key in (new_keys - pressed_wasd):
        press_key(key)

    pressed_wasd = new_keys


def handle_joystick_aim(x: float, y: float):
    """Right joystick → relative mouse movement."""
    if abs(x) > 0.05 or abs(y) > 0.05:
        dx = int(x * AIM_MULTIPLIER)
        dy = int(y * AIM_MULTIPLIER)
        mouse.move(dx, dy)


# ── Skill-aim joystick → mouse move + skill key tap ──────────────────────────
# BUG FIX: Made async so we can use `await asyncio.sleep` instead of
# `time.sleep`, which was blocking the entire event loop and causing WebSocket
# disconnects ("no close frame received or sent").
#
# BUG FIX 2: Cursor drift — previously moved mouse by full (dx,dy) on EVERY
# aiming event, so holding the joystick still made cursor fly off-screen.
# Now we track the last offset per action and only move by the DELTA.
async def handle_skill_aim(data: dict):
    global skill_last_cast, skill_aim_last
    action    = data.get("action", "")
    state     = data.get("state", "")
    angle_deg = float(data.get("angle", 0))
    magnitude = float(data.get("magnitude", 0))

    # Target cursor offset from where aiming started.
    # cos/sin of screen-space angle (Y-down), scaled by magnitude × max distance.
    angle_rad  = math.radians(angle_deg)
    target_dx  = math.cos(angle_rad) * magnitude * SKILL_AIM_DISTANCE
    target_dy  = math.sin(angle_rad) * magnitude * SKILL_AIM_DISTANCE

    if state == "aiming":
        # Only move by the DELTA from last sent offset so cursor stays at the
        # direction position instead of accumulating drift every frame.
        last_dx, last_dy = skill_aim_last.get(action, (0.0, 0.0))
        delta_x = target_dx - last_dx
        delta_y = target_dy - last_dy
        if abs(delta_x) > 0.5 or abs(delta_y) > 0.5:
            mouse.move(int(delta_x), int(delta_y))
        skill_aim_last[action] = (target_dx, target_dy)
        print(f"[SKILL_AIM] aiming action={action!r} angle={angle_deg:.1f}° "
              f"mag={magnitude:.2f} → target({target_dx:.0f},{target_dy:.0f}) "
              f"delta({delta_x:.0f},{delta_y:.0f})")

    elif state == "cast":
        # Per-action cooldown
        now  = time.monotonic()
        last = skill_last_cast.get(action, 0.0)
        if now - last < SKILL_CAST_COOLDOWN:
            print(f"[SKILL_AIM] cast COOLDOWN action={action!r}, skipping")
            return
        skill_last_cast[action] = now

        # Move cursor by remaining delta to reach final aim position.
        last_dx, last_dy = skill_aim_last.get(action, (0.0, 0.0))
        delta_x = target_dx - last_dx
        delta_y = target_dy - last_dy
        if abs(delta_x) > 0.5 or abs(delta_y) > 0.5:
            mouse.move(int(delta_x), int(delta_y))

        # Clear tracking so next skill aim starts fresh.
        skill_aim_last.pop(action, None)

        # Resolve and tap the skill key
        key_str = resolve_action_key(action)
        key     = resolve_key(key_str)
        print(f"[SKILL_AIM] cast action={action!r} key_str={key_str!r} key={key!r} "
              f"angle={angle_deg:.1f}° mag={magnitude:.2f}")
        keyboard.press(key)
        await asyncio.sleep(0.05)   # ← was time.sleep() — was blocking event loop!
        keyboard.release(key)

        # Return cursor to center after cast so next aim starts from same spot.
        mouse.move(int(-target_dx), int(-target_dy))


# ── Button press/release ──────────────────────────────────────────────────────
def handle_button(data: dict):
    # Supports both {"type":"button","key":"k","state":"down"} and
    #               {"type":"dpad", "key":"w","state":"down"}
    raw_name = data.get("key", data.get("button", ""))
    action   = data.get("state", data.get("action", ""))
    key_str  = resolve_action_key(raw_name)
    if not key_str:
        print(f"[BUTTON] WARNING: no key for raw_name={raw_name!r}")
        return
    print(f"[BUTTON] name={raw_name!r} → key={key_str!r} action={action!r}")
    if action == "down":
        press_key(key_str)
    elif action == "up":
        release_key(key_str)


# ── Mouse move (from touchpad drag) ──────────────────────────────────────────
def handle_mouse_move(data: dict):
    dx = float(data.get("dx", 0))
    dy = float(data.get("dy", 0))
    if abs(dx) > 0.5 or abs(dy) > 0.5:
        print(f"[MOUSE_MOVE] dx={dx:.1f} dy={dy:.1f}")
        mouse.move(int(dx), int(dy))


# ── Mouse click ───────────────────────────────────────────────────────────────
def handle_mouse_click(data: dict):
    btn_str = data.get("button", "left")
    state   = data.get("state", "down")
    btn     = Button.left if btn_str == "left" else Button.right
    print(f"[MOUSE_CLICK] button={btn_str} state={state}")
    if state == "down":
        mouse.press(btn)
    elif state == "up":
        mouse.release(btn)


# ── Mouse scroll ──────────────────────────────────────────────────────────────
def handle_mouse_scroll(data: dict):
    dy = float(data.get("dy", 0))
    print(f"[MOUSE_SCROLL] dy={dy:.2f}")
    mouse.scroll(0, dy)


# ── WebSocket message dispatcher ──────────────────────────────────────────────
async def handler(websocket):
    print(f"[SERVER] Client connected: {websocket.remote_address}")
    try:
        async for message in websocket:
            try:
                data     = json.loads(message)
                msg_type = data.get("type")

                print(f"[RECV] {data}")

                if msg_type == "joystick":
                    stick = data.get("stick", "")
                    x     = float(data.get("x", 0))
                    y     = float(data.get("y", 0))
                    if stick == "movement":
                        handle_joystick_movement(x, y)
                    elif stick == "aim":
                        handle_joystick_aim(x, y)

                elif msg_type == "button":
                    handle_button(data)

                elif msg_type == "dpad":
                    handle_button(data)

                elif msg_type == "skill_aim":
                    await handle_skill_aim(data)

                # Touchpad mouse movement — Android sends "mouse_move"
                elif msg_type in ("mouse_move", "mouse"):
                    handle_mouse_move(data)

                elif msg_type == "mouse_click":
                    handle_mouse_click(data)

                elif msg_type == "mouse_scroll":
                    handle_mouse_scroll(data)

                # Ignore profile sync messages silently
                elif msg_type == "profile":
                    pass

                else:
                    print(f"[RECV] Unknown type: {msg_type!r}")

            except Exception as e:
                import traceback
                print(f"[ERROR] {e}")
                traceback.print_exc()

    except websockets.exceptions.ConnectionClosed as e:
        print(f"[SERVER] Client disconnected: {websocket.remote_address} ({e})")
    finally:
        # Release all held movement keys on disconnect
        global pressed_wasd
        print("[SERVER] Releasing all held keys on disconnect")
        for key in list(pressed_wasd):
            release_key(key)
        pressed_wasd.clear()


async def main():
    # ping_interval=None: disables automatic WebSocket pings.
    # Without this, the websockets library closes the connection if a client
    # doesn't respond to a ping within the timeout — causing "no close frame"
    # errors on the Android side when the device is under load.
    async with websockets.serve(handler, "0.0.0.0", PORT, ping_interval=None):
        print(f"[SERVER] WebSocket server listening on ws://0.0.0.0:{PORT}")
        await asyncio.Future()   # run forever


if __name__ == "__main__":
    asyncio.run(main())
