import logging
import threading
from typing import Dict, Any
from core.models import ControllerState

logger = logging.getLogger("ConsensiusServer")

class StateManager:
    def __init__(self):
        self._state = ControllerState()
        self._lock = threading.Lock()

    def update_joystick(self, stick_type: str, x: float, y: float) -> None:
        """Updates the joystick state coordinates in a thread-safe manner."""
        with self._lock:
            if stick_type == "left":
                self._state.left.x = x
                self._state.left.y = y
            elif stick_type == "right":
                self._state.right.x = x
                self._state.right.y = y
            else:
                logger.warning(f"Unknown joystick type: {stick_type}")

    def update_button(self, button_name: str, action: str) -> None:
        """Updates the button state in state dictionary in a thread-safe manner."""
        with self._lock:
            # action is typically "down" or "up"
            self._state.buttons[button_name] = action

    def get_state(self) -> ControllerState:
        """Returns the current controller state in a thread-safe manner."""
        with self._lock:
            return self._state

    def reset_state(self) -> None:
        """Resets joystick and button states back to default in a thread-safe manner."""
        with self._lock:
            self._state.left.x = 0.0
            self._state.left.y = 0.0
            self._state.right.x = 0.0
            self._state.right.y = 0.0
            self._state.buttons.clear()
        logger.info("Controller state reset.")
