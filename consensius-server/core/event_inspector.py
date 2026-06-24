import time
import logging
from collections import deque
import threading
from typing import List, Dict, Any, Optional

logger = logging.getLogger("ConsensiusServer")

class EventInspector:
    def __init__(self, event_bus, server):
        self.event_bus = event_bus
        self.server = server
        
        self.debug_mode = True # Default ON: show all events
        self._buffer = deque(maxlen=100)
        self._lock = threading.Lock()
        self.last_latency_ms = 0.0

        # Subscribe to Event Bus
        self.event_bus.subscribe("action_event", self._on_action_event)
        self.event_bus.subscribe("joystick_event", self._on_joystick_event)
        self.event_bus.subscribe("move_event", self._on_move_event)

    def _on_move_event(self, data: Dict[str, Any]) -> None:
        with self._lock:
            event_str = f"[MOVE] {data['key']} {data['state']}"
            self._buffer.append(event_str)

    def set_debug_mode(self, enabled: bool) -> None:
        with self._lock:
            self.debug_mode = enabled
            logger.info(f"[EventInspector] Debug Mode set to {'ON' if enabled else 'OFF'}")

    def _on_action_event(self, data: Dict[str, Any]) -> None:
        output_time = time.time()
        # Compute latency based on WebSocket receive time
        recv_time = self.server.last_recv_time if (self.server and self.server.last_recv_time) else output_time
        latency = max(0.0, (output_time - recv_time) * 1000.0)
        
        with self._lock:
            self.last_latency_ms = latency
            # Format: [ENGINE] skill1 → q
            event_str = f"[ENGINE] {data['action']} → {data['mapped_key']} ({data['state'].upper()})"
            self._buffer.append(event_str)

    def _on_joystick_event(self, data: Dict[str, Any]) -> None:
        # Ignore joystick events in minimal log mode (debug mode OFF)
        with self._lock:
            if not self.debug_mode:
                return

        output_time = time.time()
        recv_time = self.server.last_recv_time if (self.server and self.server.last_recv_time) else output_time
        latency = max(0.0, (output_time - recv_time) * 1000.0)

        with self._lock:
            self.last_latency_ms = latency
            # Format: [ENGINE] joystick left (0.52, -0.31)
            # Skip logging if stick coordinates are idle/center to reduce noise
            if data['magnitude'] > 0.0:
                event_str = f"[ENGINE] joystick {data['stick']} ({data['x']:.2f}, {data['y']:.2f})"
                self._buffer.append(event_str)

    def get_latency(self) -> float:
        with self._lock:
            return self.last_latency_ms

    def get_events(self) -> List[str]:
        with self._lock:
            # Return newest on top (reversed copy)
            return list(reversed(self._buffer))
