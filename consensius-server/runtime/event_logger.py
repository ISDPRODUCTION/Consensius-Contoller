import os
import queue
import threading
import time
from pathlib import Path
from typing import List

# Max log file size before rotation (5 MB)
MAX_LOG_FILE_SIZE = 5 * 1024 * 1024
MAX_LOG_BACKUPS = 3  # Keep up to 3 old log files

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
        
        # Buffered file handle — opened once, flushed periodically
        self._file_handle = None
        self._open_log_file()
        
        self._thread = threading.Thread(target=self._log_worker, daemon=True)
        self._thread.start()
        # Fix #4: Track last flush time so we can flush periodically, not per-message
        self._last_flush_time: float = time.monotonic()
    
    def _open_log_file(self) -> None:
        """Open (or reopen) the log file for appending."""
        try:
            if self._file_handle and not self._file_handle.closed:
                self._file_handle.close()
            self._file_handle = open(self.log_file_path, "a", encoding="utf-8")
        except Exception:
            self._file_handle = None
    
    def _rotate_log(self) -> None:
        """Rotate server.log if it exceeds MAX_LOG_FILE_SIZE."""
        try:
            if self._file_handle and not self._file_handle.closed:
                self._file_handle.close()
            
            # Shift existing backups: .3 -> delete, .2 -> .3, .1 -> .2, log -> .1
            for i in range(MAX_LOG_BACKUPS, 0, -1):
                src = self.log_file_path if i == 1 else self.log_file_path.with_suffix(f".log.{i - 1}")
                dst = self.log_file_path.with_suffix(f".log.{i}")
                if dst.exists():
                    dst.unlink()
                if src.exists():
                    src.rename(dst)
            
            self._open_log_file()
        except Exception:
            self._open_log_file()

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

                # Fix #4: Flush to disk at most once every 2 seconds instead
                # of on every single message (was causing constant disk I/O).
                now = time.monotonic()
                if now - self._last_flush_time >= 2.0:
                    try:
                        if self._file_handle and not self._file_handle.closed:
                            self._file_handle.flush()
                    except Exception:
                        pass
                    self._last_flush_time = now
                            
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
        try:
            print(msg)
        except Exception:
            pass
        # Log to buffered file (no flush here — flushed periodically in _log_worker)
        try:
            if self._file_handle and not self._file_handle.closed:
                self._file_handle.write(msg + "\n")
                # Fix #4: Removed per-message flush(). Flush happens every 2s in _log_worker.
                # Rotate if file exceeds max size
                if self._file_handle.tell() > MAX_LOG_FILE_SIZE:
                    self._rotate_log()
            else:
                self._open_log_file()
                if self._file_handle:
                    self._file_handle.write(msg + "\n")
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
        # Flush and close the log file
        try:
            if self._file_handle and not self._file_handle.closed:
                self._file_handle.flush()
                self._file_handle.close()
        except Exception:
            pass
