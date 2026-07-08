# -*- mode: python ; coding: utf-8 -*-
# ============================================================
#  consensius_server.spec
#  PyInstaller spec file for Consensius Server
#  Usage: pyinstaller consensius_server.spec
# ============================================================

import sys
from pathlib import Path

block_cipher = None

# -- Locate customtkinter assets ---------------------------------------------
import customtkinter
CTK_DIR = Path(customtkinter.__file__).parent

# -- Collect all data files needed at runtime --------------------------------
added_files = [
    (str(CTK_DIR / "assets"), "customtkinter/assets"),
]

a = Analysis(
    ["main.py"],
    pathex=["."],
    binaries=[],
    datas=added_files,
    hiddenimports=[
        # websockets internals
        "websockets",
        "websockets.legacy",
        "websockets.legacy.server",
        "websockets.legacy.client",
        "websockets.extensions",
        "websockets.extensions.permessage_deflate",
        # pynput backends
        "pynput.keyboard._win32",
        "pynput.mouse._win32",
        # PIL/Pillow
        "PIL._imaging",
        "PIL.Image",
        "PIL.ImageDraw",
        "PIL.ImageFont",
        "PIL.ImageTk",
        # qrcode
        "qrcode",
        "qrcode.image.pil",
        # tkinter
        "tkinter",
        "tkinter.ttk",
        # customtkinter
        "customtkinter",
        # project modules -- live code only
        "network.websocket_server",
        "input.input_handler",
        "core.config_manager",
        "core.profile_manager",
        "core.event_bus",
        "core.action_mapper",
        "core.models",
        "core.state_manager",
        "ui.app_window",
        "ui.connection_page",
        "ui.profiles_page",
        "ui.monitor_page",
        "ui.settings_page",
        "ui.about_page",
    ],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[
        "matplotlib",
        "numpy",
        "scipy",
        "pandas",
        "pytest",
    ],
    win_no_prefer_redirects=False,
    win_private_assemblies=False,
    cipher=block_cipher,
    noarchive=False,
)

pyz = PYZ(a.pure, a.zipped_data, cipher=block_cipher)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.zipfiles,
    a.datas,
    [],
    name="ConsenciusServer",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    upx_exclude=[],
    runtime_tmpdir=None,
    # -- Windows-specific --
    console=False,
    disable_windowed_traceback=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
    icon="assets/icon.ico",
)
