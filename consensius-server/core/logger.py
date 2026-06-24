import logging
import sys
from pathlib import Path

class ConsensiusFormatter(logging.Formatter):
    """Custom log formatter to print RX logs as [RX] and server logs as [INFO] / [ERROR]"""
    def format(self, record):
        if record.name == "ConsensiusRX":
            return f"[RX] {record.getMessage()}"
        else:
            return f"[{record.levelname}] {record.getMessage()}"

def setup_logging(logs_dir: str = "logs"):
    # Ensure logs folder exists
    logs_path = Path(logs_dir)
    logs_path.mkdir(parents=True, exist_ok=True)

    # Root application logger setup
    logger = logging.getLogger("ConsensiusServer")
    logger.setLevel(logging.DEBUG)

    # RX logger setup
    rx_logger = logging.getLogger("ConsensiusRX")
    rx_logger.setLevel(logging.INFO)

    # Prevent logs from double-propagation
    logger.propagate = False
    rx_logger.propagate = False

    # Console Handler
    console_handler = logging.StreamHandler(sys.stdout)
    console_handler.setLevel(logging.INFO)
    console_formatter = ConsensiusFormatter()
    console_handler.setFormatter(console_formatter)

    # File Handler for server logging
    file_handler = logging.FileHandler(logs_path / "server.log", encoding="utf-8")
    file_handler.setLevel(logging.DEBUG)
    file_formatter = logging.Formatter('[%(asctime)s] [%(levelname)s] [%(name)s] %(message)s')
    file_handler.setFormatter(file_formatter)

    # Attach handlers
    logger.addHandler(console_handler)
    logger.addHandler(file_handler)
    
    rx_logger.addHandler(console_handler)
    rx_logger.addHandler(file_handler)
