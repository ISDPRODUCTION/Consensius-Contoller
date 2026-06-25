"""
network/websocket_server.py
===========================
WebSocket server (asyncio-based, runs in a background thread).
Handles: joystick, button, dpad, mouse_move, mouse_click, mouse_scroll,
         skill_aim, profile messages.

Sync fixes (matched to Android ControllerScreen.kt):
  - joystick: stick="movement" (Android) → mapped to "left" for InputHandler
  - mouse_move: Android sends type="mouse_move" (was only "mouse")
  - mouse_click: NEW — Android sends type="mouse_click" {button, state}
  - mouse_scroll: NEW — Android sends type="mouse_scroll" {dy}
  - skill_aim: distinguishes state="aiming" (mouse move only) vs state="cast"
"""
import asyncio
import json
import time
import threading
from datetime import datetime
from typing import Optional, Set, Callable, Dict, Any

try:
    import websockets
    WS_AVAILABLE = True
except ImportError:
    WS_AVAILABLE = False


class WebSocketServer:
    """
    Async WebSocket server that processes controller input messages and
    calls registered callbacks so the UI and input handler stay decoupled.
    """

    def __init__(self, settings: dict):
        self._settings = settings
        self._lock     = threading.Lock()

        # Active websocket connections
        self._connections: Set = set()

        # Server handle
        self._server = None
        self._loop: Optional[asyncio.AbstractEventLoop] = None

        # State
        self.is_running     = False
        self.start_time: Optional[float] = None
        self.client_ip: Optional[str]    = None
        self.client_profile: Optional[Dict[str, Any]] = None

        # Callbacks (set by app layer)
        self.on_log:     Optional[Callable[[str, str], None]] = None   # (message, type)
        self.on_connect: Optional[Callable[[str], None]]      = None   # (client_ip)
        self.on_disconnect: Optional[Callable[[], None]]      = None
        self.on_profile:    Optional[Callable[[dict], None]]  = None   # (profile_data)

        # Input handler reference (set externally)
        self.input_handler = None

    # ── Public API ─────────────────────────────────────────────────────────────

    def update_settings(self, settings: dict) -> None:
        with self._lock:
            self._settings = settings

    @property
    def port(self) -> int:
        with self._lock:
            return self._settings.get("port", 8765)

    @property
    def connected_count(self) -> int:
        return len(self._connections)

    # ── Lifecycle ──────────────────────────────────────────────────────────────

    async def _serve(self) -> None:
        port = self.port
        self._server = await websockets.serve(
            self._handler, "0.0.0.0", port,
            ping_interval=20, ping_timeout=10
        )
        self.is_running  = True
        self.start_time  = time.time()
        self._log(f"Server started on port {port}", "system")
        await self._server.wait_closed()

    def start_in_thread(self) -> None:
        """Start the server in its own daemon asyncio thread."""
        if self.is_running:
            return
        self._loop = asyncio.new_event_loop()
        t = threading.Thread(target=self._run_loop, daemon=True)
        t.start()

    def _run_loop(self) -> None:
        asyncio.set_event_loop(self._loop)
        try:
            self._loop.run_until_complete(self._serve())
        except Exception as e:
            self._log(f"Server error: {e}", "system")

    def stop(self) -> None:
        """Stop the server gracefully."""
        if self._server and self._loop:
            self._loop.call_soon_threadsafe(self._server.close)
        self.is_running = False
        self.start_time = None
        self._log("Server stopped", "system")

    # ── Connection handler ─────────────────────────────────────────────────────

    async def _handler(self, websocket, path: str = "") -> None:
        self._connections.add(websocket)
        try:
            addr = websocket.remote_address
            self.client_ip = f"{addr[0]}"
        except Exception:
            self.client_ip = "Unknown"

        self._log(f"Client connected: {self.client_ip}", "system")
        if self.on_connect:
            self.on_connect(self.client_ip)

        try:
            async for raw in websocket:
                await self._process(raw)
        except Exception as e:
            # Log unexpected drops (e.g. network timeout)
            self._log(f"Connection error: {e}", "system")
        finally:
            # release_all() ALWAYS fires on disconnect — clean or unexpected.
            self._connections.discard(websocket)
            self._log("Client disconnected", "system")
            if self.input_handler:
                self.input_handler.release_all()
            if self.on_disconnect:
                self.on_disconnect()

    # ── Message processing ─────────────────────────────────────────────────────

    async def _process(self, raw: str) -> None:
        try:
            data: Dict[str, Any] = json.loads(raw)
        except Exception:
            return

        msg_type = data.get("type", "")

        # ── Joystick ────────────────────────────────────────────────────────────
        if msg_type == "joystick":
            # SYNC FIX: Android sends stick="movement" for the movement joystick.
            # InputHandler.process_joystick expects stick=="left" to process WASD.
            # Map "movement" → "left" so the handler fires correctly.
            stick_raw = data.get("stick", "left")
            stick = "left" if stick_raw in ("movement", "left") else stick_raw
            x = float(data.get("x", 0.0))
            y = float(data.get("y", 0.0))
            self._log(
                f"{'left' if stick == 'left' else 'right':4s}  x:{x:+.2f} y:{y:+.2f}",
                "joystick"
            )
            if self.input_handler:
                self.input_handler.process_joystick(stick, x, y)

        # ── Button ──────────────────────────────────────────────────────────────
        elif msg_type == "button":
            key   = data.get("key", "")
            state = data.get("state", "up")
            self._log(f"{key!s:<10} → {state.upper()}", "button")
            if self.input_handler:
                self.input_handler.process_button(key, state)

        # ── D-Pad ───────────────────────────────────────────────────────────────
        elif msg_type == "dpad":
            key   = data.get("key", "")
            state = data.get("state", "up")
            self._log(f"dpad {key:<6} → {state.upper()}", "button")
            if self.input_handler:
                self.input_handler.process_dpad(key, state)

        # ── Mouse move (touchpad drag) ───────────────────────────────────────────
        # SYNC FIX: Android sends type="mouse_move"; old server only handled "mouse".
        # Accept both for backward compatibility.
        elif msg_type in ("mouse_move", "mouse"):
            dx = float(data.get("dx", 0.0))
            dy = float(data.get("dy", 0.0))
            self._log(f"move  dx:{dx:+.1f} dy:{dy:+.1f}", "mouse")
            if self.input_handler:
                self.input_handler.process_mouse(dx, dy)

        # ── Mouse click (touchpad tap: 1-tap=left, 2-tap=right, hold=drag) ──────
        # SYNC FIX: Android sends type="mouse_click" {button, state} — was missing.
        elif msg_type == "mouse_click":
            btn_str = data.get("button", "left")
            state   = data.get("state", "down")
            self._log(f"click {btn_str:<5} → {state.upper()}", "mouse")
            if self.input_handler:
                self.input_handler.process_mouse_click(btn_str, state)

        # ── Mouse scroll (2-finger drag on touchpad) ────────────────────────────
        # SYNC FIX: Android sends type="mouse_scroll" {dy} — was missing.
        elif msg_type == "mouse_scroll":
            dy = float(data.get("dy", 0.0))
            self._log(f"scroll dy:{dy:+.2f}", "mouse")
            if self.input_handler:
                self.input_handler.process_mouse_scroll(dy)

        # ── Skill aim joystick ──────────────────────────────────────────────────
        # SYNC FIX: Android sends state="aiming" (while dragging) or state="cast"
        # (on finger release). Pass state through so InputHandler can distinguish:
        #   "aiming" → mouse aim preview only (no key press)
        #   "cast"   → mouse move + skill key tap + return
        elif msg_type == "skill_aim":
            action    = data.get("action", "")
            angle     = float(data.get("angle", 0.0))
            magnitude = float(data.get("magnitude", 0.0))
            state     = data.get("state", "cast")
            self._log(
                f"{action:<8} [{state:<6}] angle:{angle:.0f}° mag:{magnitude:.2f}",
                "button"
            )
            if self.input_handler:
                self.input_handler.process_skill_aim(action, angle, magnitude, state)

        # ── Profile sync ────────────────────────────────────────────────────────
        elif msg_type == "profile":
            profile_data = data.get("data", {})
            self.client_profile = profile_data
            name = profile_data.get("name", "Unknown")
            self._log(f"Profile received: {name}", "system")
            if self.on_profile:
                self.on_profile(profile_data)

        else:
            self._log(f"Unknown msg type: {msg_type!r}", "system")

    # ── Internal helpers ───────────────────────────────────────────────────────

    def _log(self, message: str, log_type: str = "system") -> None:
        if self.on_log:
            self.on_log(message, log_type)
