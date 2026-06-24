import sys
import asyncio
import threading
from PySide6.QtWidgets import QApplication
from core.config_manager import ConfigManager
from core.profile_manager import ProfileManager
from core.state_manager import StateManager
from runtime.input_buffer import InputBuffer
from runtime.event_logger import EventLogger
from core.event_bus import EventBus
from core.input_engine import InputEngine
from core.input_normalizer import InputNormalizer
from core.event_inspector import EventInspector
from network.websocket_server import WebSocketServer
from ui.dashboard_window import DashboardWindow

from pathlib import Path

def run_async_loop(loop: asyncio.AbstractEventLoop):
    asyncio.set_event_loop(loop)
    loop.run_forever()

def main():
    BASE_DIR = Path(__file__).resolve().parent
    
    # 1. Initialize Event Logger
    event_logger = EventLogger(logs_dir=str(BASE_DIR / "logs"))
    event_logger.log_info("Consensius Host Starting...")
    
    # 2. Load configuration
    config_manager = ConfigManager(config_path=str(BASE_DIR / "config" / "config.json"))
    config = config_manager.load_config()
    
    # 3. Load profiles
    profile_manager = ProfileManager(profiles_dir=str(BASE_DIR / "profiles"))
    profile_manager.load_profiles()
    
    # Set the active profile loaded from the configuration
    active_profile_key = config.active_profile
    profile_manager.set_active_profile(active_profile_key)
    
    # Get profile name to display in the startup summary
    active_profile = profile_manager.get_active_profile()
    active_profile_name = active_profile.name if active_profile else active_profile_key
    
    # 4. Create State Manager & Input Buffer
    state_manager = StateManager()
    input_buffer = InputBuffer()
    
    # 5. Initialize Event Bus, Input Engine and Input Normalizer
    event_bus = EventBus()
    input_engine = InputEngine(state_manager, profile_manager, event_bus)
    input_normalizer = InputNormalizer(config_manager)
    
    # Subscribe logger to action events on event bus
    def log_action_event(data):
        event_logger.log_info(f"[BUS] Action: {data['action']} -> {data['mapped_key']} ({data['type']})")
        
    def log_joystick_event(data):
        if data['magnitude'] > 0.1: # Only log non-idle movements
            event_logger.log_info(f"[BUS] Joystick: {data['stick']} (Mag: {data['magnitude']:.2f})")
            
    event_bus.subscribe("action_event", log_action_event)
    event_bus.subscribe("joystick_event", log_joystick_event)
    
    # 6. Print Startup Summary
    event_logger.log_info(f"Active Profile: {active_profile_name}")
    
    # 7. Initialize WebSocket Server
    server = WebSocketServer(
        state_manager=state_manager, 
        config_manager=config_manager, 
        profile_manager=profile_manager,
        input_buffer=input_buffer,
        event_logger=event_logger,
        input_engine=input_engine,
        input_normalizer=input_normalizer
    )
    
    # 8. Start WebSocket server in background thread running asyncio loop
    loop = asyncio.new_event_loop()
    t = threading.Thread(target=run_async_loop, args=(loop,), daemon=True)
    t.start()
    
    # Schedule server starting
    asyncio.run_coroutine_threadsafe(server.start(), loop)
    
    # 8. Start Event Inspector, Movement Simulator and PySide6 Application on main thread
    event_inspector = EventInspector(event_bus, server)
    
    from executor.movement_simulator import MovementSimulator
    movement_simulator = MovementSimulator(event_bus, profile_manager)
    
    app = QApplication(sys.argv)
    
    window = DashboardWindow(
        state_manager=state_manager,
        profile_manager=profile_manager,
        config_manager=config_manager,
        server=server,
        event_logger=event_logger,
        event_inspector=event_inspector,
        movement_simulator=movement_simulator
    )
    window.show()
    
    # Run the GUI event loop (blocking call)
    exit_code = app.exec()
    
    # 9. Clean up backend on exit
    event_logger.log_info("Consensius Host stopping backend server...")
    stop_future = asyncio.run_coroutine_threadsafe(server.stop(), loop)
    
    # Wait for server shutdown to finish (max 2 seconds)
    try:
        stop_future.result(timeout=2.0)
    except Exception:
        pass
        
    loop.call_soon_threadsafe(loop.stop())
    event_logger.stop()
    sys.exit(exit_code)

if __name__ == "__main__":
    main()
