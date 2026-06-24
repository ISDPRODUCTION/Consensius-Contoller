import logging
from typing import Callable, Dict, List, Any

logger = logging.getLogger("ConsensiusServer")

class EventBus:
    def __init__(self):
        self._subscribers: Dict[str, List[Callable[[Any], None]]] = {}

    def subscribe(self, event_type: str, callback: Callable[[Any], None]) -> None:
        """Subscribes a callback to an event type."""
        if event_type not in self._subscribers:
            self._subscribers[event_type] = []
        self._subscribers[event_type].append(callback)

    def publish(self, event_type: str, data: Any) -> None:
        """Publishes event data to all subscribers of the event type."""
        logger.debug(f"[EventBus] Publish {event_type}: {data}")
        if event_type in self._subscribers:
            for callback in self._subscribers[event_type]:
                try:
                    callback(data)
                except Exception as e:
                    logger.error(f"[EventBus] Error in subscriber callback: {e}")
