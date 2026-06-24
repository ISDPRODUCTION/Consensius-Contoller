import logging
import math
from typing import Optional
from core.state_manager import StateManager
from core.profile_manager import ProfileManager
from core.action_mapper import ActionMapper
from core.event_bus import EventBus

logger = logging.getLogger("ConsensiusServer")

class InputEngine:
    def __init__(self, state_manager: StateManager, profile_manager: ProfileManager, event_bus: EventBus):
        self.state_manager = state_manager
        self.profile_manager = profile_manager
        self.event_bus = event_bus
        self.action_mapper = ActionMapper(profile_manager)
        self._last_button_states = {}

    def process_button_input(self, button_name: str, state: str) -> None:
        """Processes a single button state change in real-time, mapping and publishing it."""
        # De-duplicate state changes
        if self._last_button_states.get(button_name) == state:
            return
        self._last_button_states[button_name] = state

        mapped = self.action_mapper.map_button(button_name, state)
        if mapped:
            # Publish mapped keyboard/mouse event
            self.event_bus.publish("action_event", mapped)

            # Enforce exact debug output: [ENGINE] skill1 → q
            debug_msg = f"[ENGINE] {mapped['action']} → {mapped['mapped_key']}"
            print(debug_msg, flush=True)
            logger.info(debug_msg)

    def process_joystick_input(self, stick_type: str, x: float, y: float) -> None:
        """Processes joystick updates real-time, applying deadzones, and publishing it."""
        active_profile = self.profile_manager.get_active_profile()
        
        deadzone = 0.25
        if active_profile and hasattr(active_profile, 'controller') and active_profile.controller:
            deadzone = active_profile.controller.deadzone

        # Apply deadzone
        magnitude = math.sqrt(x*x + y*y)
        if magnitude < deadzone:
            x, y = 0.0, 0.0

        joystick_event = {
            "stick": stick_type,
            "x": x,
            "y": y,
            "magnitude": magnitude
        }
        self.event_bus.publish("joystick_event", joystick_event)
