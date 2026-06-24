import logging
from typing import Tuple
from core.config_manager import ConfigManager

logger = logging.getLogger("ConsensiusServer")

class InputNormalizer:
    def __init__(self, config_manager: ConfigManager):
        self.config_manager = config_manager

    def normalize_joystick(self, x: float, y: float, stick: str) -> Tuple[float, float]:
        """
        Normalizes joystick X and Y coordinates based on config.json preferences.
        Inverts axes if requested and logs the standard fix transformation.
        """
        config = self.config_manager.config
        
        invert_x = config.invert_x if config else False
        invert_y = config.invert_y if config else True

        norm_x = -x if invert_x else x
        norm_y = -y if invert_y else y

        # Dynamic changes logging matching prompt example: [FIX] joystick normalized left (x:0.5, y:-0.3 → y:0.3)
        changes = []
        if norm_x != x:
            changes.append(f"x:{norm_x:.1f}")
        if norm_y != y:
            changes.append(f"y:{norm_y:.1f}")
            
        change_str = ", ".join(changes)
        if change_str:
            # Clean floating point representation avoiding trailing zeros if not needed, e.g. 0.5, -0.3
            def fmt(val: float) -> str:
                return f"{val:g}"
            
            # Re-generate clean string using raw values
            raw_x_str = fmt(x)
            raw_y_str = fmt(y)
            norm_x_str = fmt(norm_x)
            norm_y_str = fmt(norm_y)
            
            changes_fmt = []
            if norm_x != x:
                changes_fmt.append(f"x:{norm_x_str}")
            if norm_y != y:
                changes_fmt.append(f"y:{norm_y_str}")
            change_str_fmt = ", ".join(changes_fmt)
            
            log_msg = f"[FIX] joystick normalized {stick} (x:{raw_x_str}, y:{raw_y_str} → {change_str_fmt})"
            print(log_msg, flush=True)
            logger.info(log_msg)

        return norm_x, norm_y
