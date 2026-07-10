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

        # Fix #7: Joystick deduplication — skip identical consecutive values
        # Android FPS loop sends joystick at e.g. 60fps even when the stick
        # hasn't moved. Caching last (x, y) per stick and skipping duplicate
        # messages reduces InputHandler + UI log work by up to 90% when idle.
        self._last_joystick: dict = {}   # stick_name -> (x, y)
        self._last_skill: dict = {}      # action -> (angle, mag, state)
        self._log_throttles: dict = {}   # type -> next_log_time

    def _should_log(self, log_key: str, throttle_sec: float = 0.5) -> bool:
        """Limits logging frequency for high-spam events to save CPU/UI updates."""
        now = time.time()
        next_time = self._log_throttles.get(log_key, 0)
        if now >= next_time:
            self._log_throttles[log_key] = now + throttle_sec
            return True
        return False

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
            # Fix #7: Clear joystick dedup cache so next connection starts fresh
            self._last_joystick.clear()
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
            # SYNC FIX: Android sends stick="movement" (left) and skill_aim sends via skill_aim event.
            # But just in case, we accept "movement" or "left" or "camera" or "right"
            stick_raw = data.get("stick", "left")
            if stick_raw == "movement":
                stick = "left"
            elif stick_raw == "camera":
                stick = "right"
            else:
                stick = stick_raw
                
            x = float(data.get("x", 0.0))
            y = float(data.get("y", 0.0))

            # Fix #7: Deduplication — skip if (x, y) hasn't changed meaningfully.
            # Android FPS loop sends joystick continuously even when the stick is
            # stationary. Skipping duplicates cuts downstream work by ~90% at idle.
            prev = self._last_joystick.get(stick)
            if prev is not None and abs(prev[0] - x) < 0.01 and abs(prev[1] - y) < 0.01:
                return  # identical to last frame, nothing to do
            self._last_joystick[stick] = (x, y)

            if self._should_log(f"joystick_{stick}"):
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
            if dx == 0 and dy == 0:
                return
            if self._should_log("mouse_move", throttle_sec=0.2):
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
            aim_stick = data.get("aimStick", "mouse")   # "mouse", "right", "left"
            extra_keys = data.get("extraKeys", [])       # list of extra buttons to hold
            
            # Deduplicate skill aim events
            prev_skill = self._last_skill.get(action)
            if prev_skill is not None and state == "aiming":
                p_angle, p_mag, p_state = prev_skill
                if p_state == "aiming" and abs(p_angle - angle) < 1.0 and abs(p_mag - magnitude) < 0.01:
                    return
            self._last_skill[action] = (angle, magnitude, state)
            
            if self._should_log(f"skill_{action}") or state == "cast":
                self._log(
                    f"{action:<8} [{state:<6}] angle:{angle:.0f} deg mag:{magnitude:.2f} aim:{aim_stick}",
                    "button"
                )
            if self.input_handler:
                self.input_handler.process_skill_aim(action, angle, magnitude, state, aim_stick, extra_keys)

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
