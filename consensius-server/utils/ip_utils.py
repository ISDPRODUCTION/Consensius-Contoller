"""Utility: detect local IP address."""
import socket


def get_local_ip() -> str:
    """Returns the best-guess local LAN IP address."""
    try:
        # Connect to a public address (doesn't actually send data)
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.connect(("8.8.8.8", 80))
            return s.getsockname()[0]
    except Exception:
        return "127.0.0.1"
