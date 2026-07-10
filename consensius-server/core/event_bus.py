import logging
from typing import Callable, Dict, List, Any
import queue
import threading

logger = logging.getLogger("ConsensiusServer")

class EventBus:
    def __init__(self):
        self._subscribers: Dict[str, List[Callable[[Any], None]]] = {}
        self._event_queue = queue.Queue()
        self._running = True
        self._worker_thread = threading.Thread(target=self._process_events, daemon=True)
        self._worker_thread.start()

    def _process_events(self):
        """Processes events in a background thread to prevent blocking during heavy load."""
        while self._running:
            try:
                # Use a small timeout so we can exit cleanly
                event_type, data = self._event_queue.get(timeout=0.1)
                
                # We skip detailed logging on high frequency events unless in debug mode to save IO
                if event_type not in ["input_event"]: 
                    logger.debug(f"[EventBus] Process {event_type}")

                if event_type in self._subscribers:
                    for callback in self._subscribers[event_type]:
                        try:
                            callback(data)
                        except Exception as e:
                            logger.error(f"[EventBus] Error in subscriber callback for {event_type}: {e}")
                self._event_queue.task_done()
            except queue.Empty:
                continue
            except Exception as e:
                logger.error(f"[EventBus] Error processing event queue: {e}")

    def stop(self):
        self._running = False
        if self._worker_thread.is_alive():
            self._worker_thread.join(timeout=1.0)

    def subscribe(self, event_type: str, callback: Callable[[Any], None]) -> None:
        """Subscribes a callback to an event type."""
        if event_type not in self._subscribers:
            self._subscribers[event_type] = []
        self._subscribers[event_type].append(callback)

    def publish(self, event_type: str, data: Any) -> None:
        """Publishes event data to all subscribers by placing it in the queue."""
        if self._running:
            self._event_queue.put((event_type, data))

