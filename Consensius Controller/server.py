import asyncio
import json
import websockets
from pynput.keyboard import Controller as KController, Key
from pynput.mouse import Controller as MController

# Initialize controllers
keyboard = KController()
mouse = MController()

# Port to listen on
PORT = 8080

# Key mapping for left joystick (WASD)
WASD_MAP = {
    'W': 'w',
    'A': 'a',
    'S': 's',
    'D': 'd'
}

# Currently pressed movement keys to track state and avoid duplicate press/release events
pressed_movement_keys = set()

# Button mapping to keyboard keys
BUTTON_MAP = {
    "skill1": "j",
    "skill2": "k",
    "skill3": "l",
    "attack": "space",
    "spell": "i",
    "recall": "b",
    "regen": "h"
}

# Screen/aim multiplier for joystick control
AIM_MULTIPLIER = 15.0  

def handle_joystick(stick_type, x, y):
    if stick_type == "left":
        # Left joystick: Movement -> WASD
        threshold = 0.3
        
        target_keys = set()
        if y < -threshold:
            target_keys.add('W')
        elif y > threshold:
            target_keys.add('S')
            
        if x < -threshold:
            target_keys.add('A')
        elif x > threshold:
            target_keys.add('D')
            
        # Determine keys to press and keys to release
        to_press = target_keys - pressed_movement_keys
        to_release = pressed_movement_keys - target_keys
        
        for key_char in to_press:
            key = WASD_MAP[key_char]
            keyboard.press(key)
            pressed_movement_keys.add(key_char)
            
        for key_char in to_release:
            key = WASD_MAP[key_char]
            keyboard.release(key)
            pressed_movement_keys.remove(key_char)
            
    elif stick_type == "right":
        # Right joystick: Aiming -> Mouse relative movement
        if abs(x) > 0.05 or abs(y) > 0.05:
            dx = int(x * AIM_MULTIPLIER)
            dy = int(y * AIM_MULTIPLIER)
            mouse.move(dx, dy)

def handle_button(button_name, action):
    key_str = BUTTON_MAP.get(button_name)
    if not key_str:
        return
        
    # Translate special key names
    if key_str == "space":
        key = Key.space
    else:
        key = key_str
        
    if action == "down":
        keyboard.press(key)
    elif action == "up":
        keyboard.release(key)

async def handler(websocket):
    print(f"Client connected: {websocket.remote_address}")
    try:
        async for message in websocket:
            try:
                data = json.loads(message)
                print(f"Received: {data}")
                
                msg_type = data.get("type")
                if msg_type == "joystick":
                    stick = data.get("stick")
                    x = float(data.get("x", 0))
                    y = float(data.get("y", 0))
                    handle_joystick(stick, x, y)
                elif msg_type == "button":
                    btn = data.get("button")
                    action = data.get("action")
                    handle_button(btn, action)
            except Exception as e:
                print(f"Error parsing/handling message: {e}")
    except websockets.exceptions.ConnectionClosed as e:
        print(f"Client disconnected: {websocket.remote_address} ({e})")
    finally:
        # Release all pressed movement keys on disconnect to prevent stuck movement
        for key_char in list(pressed_movement_keys):
            keyboard.release(WASD_MAP[key_char])
        pressed_movement_keys.clear()

async def main():
    async with websockets.serve(handler, "0.0.0.0", PORT):
        print(f"Python WebSocket Server listening on ws://0.0.0.0:{PORT}")
        await asyncio.Future()  # run forever

if __name__ == "__main__":
    asyncio.run(main())
