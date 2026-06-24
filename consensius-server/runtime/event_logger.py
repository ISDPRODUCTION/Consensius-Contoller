import queue
import threading
from pathlib import Path
from typing import List

class EventLogger:
    def __init__(self, logs_dir: str = "logs"):
        self.logs_dir = Path(logs_dir)
        self.logs_dir.mkdir(parents=True, exist_ok=True)
        self.log_file_path = self.logs_dir / "server.log"
        self._queue = queue.Queue()
        self._stop_event = threading.Event()
        
        # Memory storage for PySide6 UI to fetch without blocking
        self._recent_logs: List[str] = []
        self._recent_rx: List[str] = []
        self._lock = threading.Lock()
        
        self._thread = threading.Thread(target=self._log_worker, daemon=True)
        self._thread.start()

    def _log_worker(self) -> None:
        while not self._stop_event.is_set() or not self._queue.empty():
            try:
                # Use small timeout to allow checking _stop_event
                msg = self._queue.get(timeout=0.1)
                self._write_log(msg)
                
                # Store in memory buffers
                with self._lock:
                    self._recent_logs.append(msg)
                    if len(self._recent_logs) > 200:
                        self._recent_logs.pop(0)
                        
                    if msg.startswith("[RX]"):
                        self._recent_rx.append(msg)
                        if len(self._recent_rx) > 100:
                            self._recent_rx.pop(0)
                            
                self._queue.task_done()
            except queue.Empty:
                continue
            except Exception as e:
                try:
                    print(f"[ERROR] Logging worker exception: {e}")
                except Exception:
                    pass

    def _write_log(self, msg: str) -> None:
        # Log to console
        print(msg, flush=True)
        # Log to file
        try:
            with open(self.log_file_path, "a", encoding="utf-8") as f:
                f.write(msg + "\n")
        except Exception as e:
            try:
                print(f"[ERROR] Failed to write log file: {e}")
            except Exception:
                pass

    def get_recent_logs(self) -> List[str]:
        """Returns snapshot of all recent logs."""
        with self._lock:
            return list(self._recent_logs)

    def get_recent_rx_events(self) -> List[str]:
        """Returns snapshot of recent RX events (newest first)."""
        with self._lock:
            # We reverse to place newest events on top
            return list(reversed(self._recent_rx))

    def log_info(self, message: str) -> None:
        self._queue.put(f"[INFO] {message}")

    def log_warning(self, message: str) -> None:
        self._queue.put(f"[WARNING] {message}")

    def log_error(self, message: str) -> None:
        self._queue.put(f"[ERROR] {message}")

    def log_rx(self, message: str) -> None:
        self._queue.put(f"[RX] {message}")

    def stop(self) -> None:
        self._stop_event.set()
        if self._thread.is_alive():
            self._thread.join(timeout=1.0)
