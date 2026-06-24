from collections import deque
import threading
from typing import List, Any

class InputBuffer:
    def __init__(self):
        self._queue = deque()
        self._lock = threading.Lock()

    def push(self, item: Any) -> None:
        """Pushes an incoming validated input into the queue in a thread-safe manner."""
        with self._lock:
            self._queue.append(item)

    def pop_all(self) -> List[Any]:
        """Pops and returns all items currently in the queue, clearing the queue thread-safely."""
        with self._lock:
            items = list(self._queue)
            self._queue.clear()
            return items

    def __len__(self) -> int:
        with self._lock:
            return len(self._queue)
