import asyncio
import json
import time
from datetime import datetime
import websockets
from typing import Optional, Set
from core.state_manager import StateManager
from core.config_manager import ConfigManager
from core.profile_manager import ProfileManager
from core.protocol_validator import ProtocolValidator
from core.input_engine import InputEngine
from core.input_normalizer import InputNormalizer
from runtime.input_buffer import InputBuffer
from runtime.event_logger import EventLogger

class WebSocketServer:
    def __init__(
        self, 
        state_manager: StateManager, 
        config_manager: ConfigManager, 
        profile_manager: ProfileManager,
        input_buffer: InputBuffer,
        event_logger: EventLogger,
        input_engine: Optional[InputEngine] = None,
        input_normalizer: Optional[InputNormalizer] = None
    ):
        self.state_manager = state_manager
        self.config_manager = config_manager
        self.profile_manager = profile_manager
        self.input_buffer = input_buffer
        self.event_logger = event_logger
        self.input_engine = input_engine
        self.input_normalizer = input_normalizer
        
        self._server: Optional[websockets.WebSocketServer] = None
        self._active_connections: Set = set()
        
        # Diagnostics/Metrics fields
        self.is_running = False
        self.start_time: Optional[float] = None
        self.last_client_address: Optional[str] = None
        self.last_client_time: Optional[str] = None
        self.last_recv_time: Optional[float] = None

    @property
    def port(self) -> int:
        config = self.config_manager.config
        return config.port if config else 8080

    @property
    def connected_count(self) -> int:
        return len(self._active_connections)

    async def start(self) -> None:
        """Starts the WebSocket server and listens on the configured port."""
        config = self.config_manager.config
        if not config:
            self.event_logger.log_error("Cannot start server: Configuration not loaded.")
            return

        port = config.port
        self._server = await websockets.serve(self._handler, "0.0.0.0", port)
        self.is_running = True
        self.start_time = time.time()
        self.event_logger.log_info(f"WebSocket running on port {port}")

    async def stop(self) -> None:
        """Stops the WebSocket server."""
        if self._server:
            self._server.close()
            await self._server.wait_closed()
            self.is_running = False
            self.start_time = None
            self.event_logger.log_info("WebSocket Server Stopped.")

    async def _handler(self, websocket, path: str = "") -> None:
        """Handles connection lifecycle and incoming messages."""
        self._active_connections.add(websocket)
        
        try:
            addr = websocket.remote_address
            self.last_client_address = f"{addr[0]}:{addr[1]}"
        except Exception:
            self.last_client_address = "Unknown"
        self.last_client_time = datetime.now().strftime("%H:%M:%S")
        
        self.event_logger.log_info("Client Connected")
        
        try:
            async for message in websocket:
                try:
                    await self._process_message(message)
                except Exception as e:
                    self.event_logger.log_error(f"Error processing message: {e}")
        except websockets.exceptions.ConnectionClosed:
            pass
        finally:
            self._active_connections.remove(websocket)
            if not self._active_connections:
                self.state_manager.reset_state()
            self.event_logger.log_info("Client Disconnected")

    async def _process_message(self, raw_message: str) -> None:
        """Parses, validates, buffers, updates state, and logs the message."""
        self.last_recv_time = time.time()
        is_valid = ProtocolValidator.validate(raw_message)
        if not is_valid:
            return

        try:
            data = json.loads(raw_message)
        except Exception:
            return

        self.input_buffer.push(data)

        msg_type = data.get("type")
        if msg_type == "joystick":
            stick = data.get("stick")
            x = float(data.get("x", 0.0))
            y = float(data.get("y", 0.0))
            
            # Normalize joystick inputs
            if self.input_normalizer:
                x, y = self.input_normalizer.normalize_joystick(x, y, stick)
                
            self.state_manager.update_joystick(stick, x, y)
            self.event_logger.log_rx(f"joystick {stick} ({x:.2f}, {y:.2f})")
            
            if self.input_engine:
                self.input_engine.process_joystick_input(stick, x, y)
            
        elif msg_type == "button":
            action = data.get("action")
            state = data.get("state")
            self.state_manager.update_button(action, state)
            self.event_logger.log_rx(f"button {action} {state.upper()}")
            
            if self.input_engine:
                self.input_engine.process_button_input(action, state)
        
        # Subscribe event log updates can propagate to server log via EventBus log output
