"""
main.py
=======
Consensius Server entry point.
- Loads settings from settings.json
- Creates WebSocketServer + InputHandler
- Launches CustomTkinter UI on main thread
- WebSocket runs in a background daemon thread
"""
import json
import sys  
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent
SETTINGS_FILE = BASE_DIR / "settings.json"


def load_settings() -> dict:
    """
    Bug 8 fix: If settings.json does not exist on first run, create it with
    default values so the server never crashes trying to read missing settings.
    """
    defaults = {
        "port":               8765,
        "start_on_launch":    False,
        "mouse_sensitivity":  1.0,
        "joystick_threshold": 0.3,
        "skill_aim_distance": 80,
        "invert_x":           False,
        "invert_y":           True,
        # skill_positions: maps action key (e.g. "j","k","l") to its screen pixel
        # position {x, y}. -1 means "not configured, use screen center".
        # Users set these via Settings → Skill Aim Positions.
        "skill_positions": {
            "j": {"x": -1, "y": -1},
            "k": {"x": -1, "y": -1},
            "l": {"x": -1, "y": -1},
            "u": {"x": -1, "y": -1},
            "i": {"x": -1, "y": -1},
            "f": {"x": -1, "y": -1},
        },
    }

    if not SETTINGS_FILE.exists():
        # First run: create settings.json with defaults
        try:
            SETTINGS_FILE.parent.mkdir(parents=True, exist_ok=True)
            with open(SETTINGS_FILE, "w") as f:
                json.dump(defaults, f, indent=2)
            print(f"[INFO] Created default settings.json at {SETTINGS_FILE}")
        except Exception as e:
            print(f"[WARN] Could not create settings.json: {e}. Using in-memory defaults.")
        return defaults

    try:
        with open(SETTINGS_FILE, "r") as f:
            loaded = json.load(f)
        # Merge: loaded values override defaults so missing keys always have a value
        defaults.update(loaded)
    except Exception as e:
        print(f"[WARN] Could not load settings.json: {e}. Using defaults.")
    return defaults


def main():
    settings = load_settings()

    # Lazily import heavy modules so errors surface clearly
    from network.websocket_server import WebSocketServer
    from input.input_handler import InputHandler
    from ui.app_window import AppWindow

    # Create server and input handler
    server        = WebSocketServer(settings)
    input_handler = InputHandler(settings)

    # Wire input handler into server
    server.input_handler = input_handler

    # Launch UI (blocks until window is closed)
    app = AppWindow(server, input_handler, settings)
    app.mainloop()


if __name__ == "__main__":
    main()
