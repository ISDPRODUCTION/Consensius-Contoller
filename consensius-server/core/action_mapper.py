from typing import Dict, Any, Optional
from core.profile_manager import ProfileManager

class ActionMapper:
    def __init__(self, profile_manager: ProfileManager):
        self.profile_manager = profile_manager

    def map_button(self, button_name: str, state: str) -> Optional[Dict[str, Any]]:
        """
        Maps raw controller button state to structured action schema.
        Returns a dict containing action, mapped_key, type, and state if mapped.
        """
        active_profile = self.profile_manager.get_active_profile()
        if not active_profile:
            return None

        bindings = active_profile.bindings
        mapped_key = bindings.get(button_name)
        if not mapped_key:
            return None

        # Determine key trigger type
        key_type = "keyboard"
        if mapped_key.lower() in ("left_click", "right_click", "middle_click"):
            key_type = "mouse"

        return {
            "action": button_name,
            "mapped_key": mapped_key,
            "type": key_type,
            "state": state
        }
