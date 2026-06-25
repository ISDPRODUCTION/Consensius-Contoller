"""Utility: generate QR code image for the Consensius protocol URI."""
from PIL import Image

try:
    import qrcode
    QR_AVAILABLE = True
except ImportError:
    QR_AVAILABLE = False


def generate_qr_image(ip: str, port: int, size: int = 200) -> "Image.Image | None":
    """
    Generates a PIL Image containing a QR code that encodes:
        consensius://<ip>:<port>

    Returns None if qrcode library is not installed.
    """
    if not QR_AVAILABLE:
        return None

    uri = f"consensius://{ip}:{port}"

    qr = qrcode.QRCode(
        version=1,
        error_correction=qrcode.constants.ERROR_CORRECT_L,
        box_size=6,
        border=3,
    )
    qr.add_data(uri)
    qr.make(fit=True)

    img = qr.make_image(fill_color="#0084FF", back_color="#0D1117")
    img = img.resize((size, size), Image.LANCZOS)
    return img
