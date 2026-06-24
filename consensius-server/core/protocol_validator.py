import json
from typing import Any

class ProtocolValidator:
    @staticmethod
    def validate(raw_message: str) -> bool:
        """
        Parses and validates raw message string.
        Returns True if message is a valid joystick or button message, False otherwise.
        """
        try:
            if not isinstance(raw_message, (str, bytes)):
                return False
            data = json.loads(raw_message)
        except Exception:
            return False
            
        if not isinstance(data, dict):
            return False
            
        msg_type = data.get("type")
        if msg_type == "joystick":
            stick = data.get("stick")
            if stick not in ("left", "right"):
                return False
            
            x = data.get("x")
            y = data.get("y")
            
            # Must be float or int and within -1.0 to 1.0 range
            if not isinstance(x, (int, float)) or isinstance(x, bool):
                return False
            if not isinstance(y, (int, float)) or isinstance(y, bool):
                return False
            
            x_val = float(x)
            y_val = float(y)
            if not (-1.0 <= x_val <= 1.0) or not (-1.0 <= y_val <= 1.0):
                return False
                
            return True
            
        elif msg_type == "button":
            action = data.get("action")
            state = data.get("state")
            if not isinstance(action, str) or not action:
                return False
            if state not in ("down", "up"):
                return False
                
            return True
            
        return False
