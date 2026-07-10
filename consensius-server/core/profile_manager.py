import json
import logging
from pathlib import Path
from typing import Dict, Optional, List, Any
from datetime import datetime
from core.models import Profile, ProfileControllerSettings, ProfileMetadata

logger = logging.getLogger("ConsensiusServer")

class ProfileManager:
    def __init__(self, profiles_dir: str = "profiles"):
        self.profiles_dir = Path(profiles_dir)
        self.profiles: Dict[str, Profile] = {}
        self.active_profile_key: Optional[str] = None
        self.load_profiles()

    def load_profiles(self) -> Dict[str, Profile]:
        """Loads and validates all profiles from profiles directory safely."""
        if not self.profiles_dir.exists():
            logger.warning(f"Profiles directory not found at {self.profiles_dir}. Creating directory.")
            self.profiles_dir.mkdir(parents=True, exist_ok=True)
            
        loaded_profiles = {}
        # Ensure default xbox360 profile exists
        xbox_path = self.profiles_dir / "xbox360.json"
        if not xbox_path.exists():
            now_str = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
            xbox_profile = Profile(
                id="xbox360",
                name="Xbox 360 (Virtual Gamepad)",
                bindings={
                    "a": "a",
                    "b": "b",
                    "x": "x",
                    "y": "y",
                    "lb": "lb",
                    "rb": "rb",
                    "lt": "lt",
                    "rt": "rt",
                    "back": "back",
                    "start": "start",
                    "l3": "l3",
                    "r3": "r3",
                    "dpad_up": "dpad_up",
                    "dpad_down": "dpad_down",
                    "dpad_left": "dpad_left",
                    "dpad_right": "dpad_right"
                },
                controller=ProfileControllerSettings(
                    deadzone=0.25,
                    sensitivity=1.0,
                    send_rate=60
                ),
                metadata=ProfileMetadata(
                    created_at=now_str,
                    updated_at=now_str
                )
            )
            try:
                with open(xbox_path, "w", encoding="utf-8") as f:
                    json.dump(xbox_profile.to_dict(), f, indent=2)
            except Exception as e:
                logger.error(f"Failed to create default xbox360 profile: {e}")

        for file_path in self.profiles_dir.glob("*.json"):
            profile_id = file_path.stem
            try:
                with open(file_path, "r", encoding="utf-8") as f:
                    data = json.load(f)
                
                if self.validate_profile_dict(data):
                    pid = data.get("id", profile_id)
                    ctrl_data = data.get("controller", {})
                    meta_data = data.get("metadata", {})
                    
                    profile = Profile(
                        id=pid,
                        name=data["name"],
                        bindings=data.get("bindings", {}),
                        controller=ProfileControllerSettings(
                            deadzone=float(ctrl_data.get("deadzone", 0.25)),
                            sensitivity=float(ctrl_data.get("sensitivity", 1.0)),
                            send_rate=int(ctrl_data.get("send_rate", 60))
                        ),
                        metadata=ProfileMetadata(
                            created_at=meta_data.get("created_at", ""),
                            updated_at=meta_data.get("updated_at", "")
                        )
                    )
                    loaded_profiles[pid] = profile
                else:
                    logger.warning(f"Profile structure invalid: {file_path.name}")
            except Exception as e:
                logger.error(f"Failed to load profile {file_path.name}: {e}")
                
        self.profiles = loaded_profiles
        logger.info(f"Loaded {len(self.profiles)} profiles.")
        return self.profiles

    def get_profiles(self) -> List[Profile]:
        """Returns list of loaded profiles."""
        return list(self.profiles.values())

    def get_profile(self, profile_id: str) -> Optional[Profile]:
        """Returns the profile with the given ID."""
        return self.profiles.get(profile_id)

    def create_profile(self, profile_id: str, name: str) -> Optional[Profile]:
        """Creates a new profile with the default structure."""
        if not profile_id or not name:
            return None
        profile_id = profile_id.strip().lower()
        if " " in profile_id or not profile_id.isalnum():
            logger.error("Profile ID must be lowercase alphanumeric, with no spaces.")
            return None
            
        file_path = self.profiles_dir / f"{profile_id}.json"
        if profile_id in self.profiles or file_path.exists():
            logger.error(f"Profile ID '{profile_id}' already exists.")
            return None

            
        now_str = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        new_profile = Profile(
            id=profile_id,
            name=name,
            bindings={
                "attack": "space",
                "skill1": "q",
                "skill2": "e",
                "skill3": "r"
            },
            controller=ProfileControllerSettings(),
            metadata=ProfileMetadata(created_at=now_str, updated_at=now_str)
        )
        
        self.profiles[profile_id] = new_profile
        self.save_profile(new_profile)
        return new_profile

    def delete_profile(self, profile_id: str) -> bool:
        """Deletes a profile if it exists and is not active."""
        if profile_id == "xbox360":
            logger.error("Cannot delete the permanent xbox360 profile.")
            return False

        if profile_id == self.active_profile_key:
            logger.error("Cannot delete the active profile.")
            return False
            
        if profile_id in self.profiles:
            del self.profiles[profile_id]
            file_path = self.profiles_dir / f"{profile_id}.json"
            try:
                if file_path.exists():
                    file_path.unlink()
                logger.info(f"Deleted profile file: {file_path.name}")
                return True
            except Exception as e:
                logger.error(f"Failed to delete profile file {file_path.name}: {e}")
                return False
        return False

    def save_profile(self, profile: Profile) -> bool:
        """Saves a profile to JSON file safely."""
        try:
            profile.metadata.updated_at = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
            file_path = self.profiles_dir / f"{profile.id}.json"
            self.profiles_dir.mkdir(parents=True, exist_ok=True)
            
            with open(file_path, "w", encoding="utf-8") as f:
                json.dump(profile.to_dict(), f, indent=2)
            logger.info(f"Successfully saved profile: {profile.id}")
            return True
        except Exception as e:
            logger.error(f"Failed to save profile {profile.id}: {e}")
            return False

    def set_active_profile(self, profile_id: str) -> bool:
        """Sets the active profile key."""
        if profile_id in self.profiles:
            self.active_profile_key = profile_id
            logger.info(f"Active Profile set to: {self.profiles[profile_id].name}")
            return True
        logger.error(f"Profile '{profile_id}' not found.")
        return False

    def get_active_profile(self) -> Optional[Profile]:
        """Returns the active profile."""
        if not self.active_profile_key:
            return None
        return self.profiles.get(self.active_profile_key)

    def validate_profile_dict(self, data: Dict[str, Any]) -> bool:
        """Validates structural properties of a profile dictionary."""
        if not isinstance(data, dict):
            return False
        if "id" not in data or "name" not in data:
            return False
        if not isinstance(data["id"], str) or not isinstance(data["name"], str):
            return False
        return True
