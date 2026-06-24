import logging
from typing import Dict, Any
import threading

logger = logging.getLogger("ConsensiusServer")

class MovementSimulator:
    def __init__(self, event_bus, profile_manager):
        self.event_bus = event_bus
        self.profile_manager = profile_manager
        
        # State tracking: True = DOWN (ON), False = UP (OFF)
        self.states = {
            "W": False,
            "A": False,
            "S": False,
            "D": False
        }
        self._lock = threading.Lock()
        
        self.event_bus.subscribe("joystick_event", self._on_joystick_event)

    def _on_joystick_event(self, data: Dict[str, Any]) -> None:
        # We only simulate movement based on left joystick
        if data.get("stick") != "left":
            return
            
        x = data.get("x", 0.0)
        y = data.get("y", 0.0)
        
        # Determine deadzone
        deadzone = 0.25
        active_profile = self.profile_manager.get_active_profile()
        if active_profile and active_profile.controller:
            deadzone = active_profile.controller.deadzone
            
        # Target states according to prompt requirements
        target_w = y > deadzone
        target_s = y < -deadzone
        target_d = x > deadzone
        target_a = x < -deadzone
        
        targets = {
            "W": target_w,
            "S": target_s,
            "D": target_d,
            "A": target_a
        }
        
        with self._lock:
            for key, target_state in targets.items():
                current_state = self.states[key]
                if current_state != target_state:
                    self.states[key] = target_state
                    state_str = "DOWN" if target_state else "UP"
                    
                    event_data = {
                        "key": key,
                        "state": state_str
                    }
                    self.event_bus.publish("move_event", event_data)
                    logger.info(f"[MOVE] {key} {state_str}")

    def get_states(self) -> Dict[str, bool]:
        with self._lock:
            return dict(self.states)
