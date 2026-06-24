from dataclasses import dataclass, field
from typing import Dict, Any

@dataclass
class JoystickState:
    x: float = 0.0
    y: float = 0.0

    def to_dict(self) -> Dict[str, float]:
        return {"x": self.x, "y": self.y}

@dataclass
class ControllerState:
    left: JoystickState = field(default_factory=JoystickState)
    right: JoystickState = field(default_factory=JoystickState)
    buttons: Dict[str, str] = field(default_factory=dict)  # maps button name -> state (e.g., "down", "up")

    def to_dict(self) -> Dict[str, Any]:
        return {
            "left": self.left.to_dict(),
            "right": self.right.to_dict(),
            "buttons": self.buttons
        }

@dataclass
class Config:
    port: int
    active_profile: str
    deadzone: float
    send_rate: int
    invert_x: bool = False
    invert_y: bool = True

    def to_dict(self) -> Dict[str, Any]:
        return {
            "port": self.port,
            "active_profile": self.active_profile,
            "deadzone": self.deadzone,
            "send_rate": self.send_rate,
            "invert_x": self.invert_x,
            "invert_y": self.invert_y
        }

@dataclass
class ProfileControllerSettings:
    deadzone: float = 0.25
    sensitivity: float = 1.0
    send_rate: int = 60

    def to_dict(self) -> Dict[str, Any]:
        return {
            "deadzone": self.deadzone,
            "sensitivity": self.sensitivity,
            "send_rate": self.send_rate
        }

@dataclass
class ProfileMetadata:
    created_at: str = ""
    updated_at: str = ""

    def to_dict(self) -> Dict[str, str]:
        return {
            "created_at": self.created_at,
            "updated_at": self.updated_at
        }

@dataclass
class Profile:
    id: str
    name: str
    bindings: Dict[str, str] = field(default_factory=dict)
    controller: ProfileControllerSettings = field(default_factory=ProfileControllerSettings)
    metadata: ProfileMetadata = field(default_factory=ProfileMetadata)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "id": self.id,
            "name": self.name,
            "bindings": self.bindings,
            "controller": self.controller.to_dict(),
            "metadata": self.metadata.to_dict()
        }
