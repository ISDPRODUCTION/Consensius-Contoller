# Consensius Server

Consensius Server acts as the backend foundation for the Consensius Controller ecosystem. It receives controller input from Android devices over WebSockets and maintains connection, configuration, and controller state.

## Project Structure

```
consensius-server/
├── main.py                    # Main Entrypoint
├── network/
│   └── websocket_server.py    # WebSocket server handling client inputs
├── core/
│   ├── state_manager.py       # Tracks current button/joystick states
│   ├── config_manager.py      # Manages configuration saving/validation
│   ├── profile_manager.py     # Parses input maps/profiles
│   ├── logger.py              # Customized logger formatted output
│   └── models.py              # Data models/schemas
├── config/
│   └── config.json            # Configuration configuration file
├── profiles/
│   ├── mlbb.json              # Mobile Legends profile
│   ├── pubg.json              # PUBG Mobile profile
│   └── custom.json            # Custom profiles
├── logs/                      # Log directory (contains server.log)
└── README.md                  # This README
```

## Setup & Running

1. **Install Dependencies**:
   Ensure you have Python 3.11+ and install the `websockets` library:
   ```bash
   pip install websockets
   ```

2. **Start the Server**:
   Run the main entrypoint:
   ```bash
   python main.py
   ```

3. **Logging System**:
   * Logs are output to the terminal with custom format (`[INFO]`, `[RX]`, etc.).
   * Complete logs are written to `logs/server.log`.
