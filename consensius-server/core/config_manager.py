import json
import logging
from pathlib import Path
from typing import Dict, Any, Optional
from core.models import Config

logger = logging.getLogger("ConsensiusServer")

class ConfigManager:
    def __init__(self, config_path: str = "config/config.json"):
        self.config_path = Path(config_path)
        self.config: Optional[Config] = None

    def load_config(self) -> Config:
        """Loads and validates configuration from JSON file."""
        if not self.config_path.exists():
            logger.warning(f"Config file not found at {self.config_path}. Creating default config.")
            self.config = Config(port=8080, active_profile="mlbb", deadzone=0.25, send_rate=60, invert_x=False, invert_y=True)
            self.save_config()
            return self.config

        try:
            with open(self.config_path, "r") as f:
                data = json.load(f)
            
            validated_data = self.validate_config(data)
            self.config = Config(**validated_data)
            logger.info("Configuration successfully loaded and validated.")
            return self.config
        except Exception as e:
            logger.error(f"Failed to load or parse configuration: {e}. Falling back to defaults.")
            self.config = Config(port=8080, active_profile="mlbb", deadzone=0.25, send_rate=60, invert_x=False, invert_y=True)
            return self.config

    def save_config(self) -> bool:
        """Saves current configuration back to JSON file."""
        if not self.config:
            logger.error("No active config to save.")
            return False
        try:
            self.config_path.parent.mkdir(parents=True, exist_ok=True)
            with open(self.config_path, "w") as f:
                json.dump(self.config.to_dict(), f, indent=2)
            logger.info("Configuration successfully saved.")
            return True
        except Exception as e:
            logger.error(f"Failed to save configuration: {e}")
            return False

    def update_config(self, updates: Dict[str, Any]) -> bool:
        """Updates configuration properties and saves changes."""
        if not self.config:
            logger.error("Cannot update config. Config has not been loaded.")
            return False
        try:
            current_dict = self.config.to_dict()
            current_dict.update(updates)
            validated_data = self.validate_config(current_dict)
            self.config = Config(**validated_data)
            return self.save_config()
        except Exception as e:
            logger.error(f"Failed to update config: {e}")
            return False

    def validate_config(self, data: Dict[str, Any]) -> Dict[str, Any]:
        """Validates configuration parameters, replacing invalid or missing keys with defaults."""
        validated = {}
        
        # Port validation
        port = data.get("port")
        if isinstance(port, int) and 1 <= port <= 65535:
            validated["port"] = port
        else:
            logger.warning(f"Invalid port: {port}. Defaulting to 8080.")
            validated["port"] = 8080

        # Active profile validation
        active_profile = data.get("active_profile")
        if isinstance(active_profile, str) and active_profile.strip():
            validated["active_profile"] = active_profile.strip()
        else:
            logger.warning(f"Invalid active_profile: {active_profile}. Defaulting to 'mlbb'.")
            validated["active_profile"] = "mlbb"

        # Deadzone validation
        deadzone = data.get("deadzone")
        try:
            deadzone_val = float(deadzone)
            if 0.0 <= deadzone_val <= 1.0:
                validated["deadzone"] = deadzone_val
            else:
                raise ValueError("deadzone must be between 0.0 and 1.0")
        except (TypeError, ValueError) as e:
            logger.warning(f"Invalid deadzone value: {deadzone}. Defaulting to 0.25. Error: {e}")
            validated["deadzone"] = 0.25

        # Send rate validation
        send_rate = data.get("send_rate")
        if isinstance(send_rate, int) and send_rate > 0:
            validated["send_rate"] = send_rate
        else:
            logger.warning(f"Invalid send_rate: {send_rate}. Defaulting to 60.")
            validated["send_rate"] = 60

        # Invert axes validation
        invert_x = data.get("invert_x")
        if isinstance(invert_x, bool):
            validated["invert_x"] = invert_x
        else:
            validated["invert_x"] = False

        invert_y = data.get("invert_y")
        if isinstance(invert_y, bool):
            validated["invert_y"] = invert_y
        else:
            validated["invert_y"] = True

        return validated
