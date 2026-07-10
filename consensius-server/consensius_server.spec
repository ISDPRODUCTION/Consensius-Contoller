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

# -- Locate vgamepad DLLs (shipped as package data, NOT as binary extensions) -
# vgamepad installs ViGEmClient.dll into:
#   vgamepad/win/vigem/client/x64/ViGEmClient.dll
#   vgamepad/win/vigem/client/x86/ViGEmClient.dll
# PyInstaller's Analysis does NOT pick these up automatically because the
# dll lives inside the package as a data file. We must add them as binaries
# so they land at sys._MEIPASS/vgamepad/win/vigem/client/<arch>/ViGEmClient.dll,
# which is exactly where vgamepad/win/vigem_client.py looks for them.
import vgamepad
VGAMEPAD_DIR = Path(vgamepad.__file__).parent
vigem_binaries = []
for dll_path in VGAMEPAD_DIR.glob("win/vigem/client/*/ViGEmClient.dll"):
    rel = dll_path.relative_to(VGAMEPAD_DIR.parent)  # e.g. vgamepad/win/...
    vigem_binaries.append((str(dll_path), str(rel.parent)))

# -- Collect all data files needed at runtime --------------------------------
added_files = [
    (str(CTK_DIR / "assets"), "customtkinter/assets"),
]

a = Analysis(
    ["main.py"],
    pathex=["."],
    binaries=vigem_binaries,
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
        # vgamepad / ViGEm
        "vgamepad",
        "vgamepad.win",
        "vgamepad.win.vigembus_gamepad",
        "vgamepad.win.virtual_gamepad",
        "vgamepad.win.vigem_client",
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
