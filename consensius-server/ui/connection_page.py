# -*- coding: utf-8 -*-
"""
ui/connection_page.py
=====================
Connection page: server status, start/stop, IP, port, connected device info,
and QR code panel.
"""
import tkinter as tk
import customtkinter as ctk
from typing import Callable, Optional
from PIL import Image

from utils.ip_utils import get_local_ip
from utils.qr_generator import generate_qr_image

# â”€â”€ Color palette â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
BG          = "#0A0A0F"
CARD        = "#0F1624"
ACCENT      = "#0084FF"
SUCCESS     = "#00E676"
ERROR       = "#FF1744"
TEXT        = "#E0E0E0"
TEXT2       = "#8899AA"
BORDER      = "#1A2535"


class ConnectionPage(ctk.CTkFrame):
    def __init__(self, master, server, on_start: Callable, on_stop: Callable, **kwargs):
        super().__init__(master, fg_color=BG, **kwargs)
        self._server    = server
        self._on_start  = on_start
        self._on_stop   = on_stop
        self._local_ip  = get_local_ip()
        self._qr_photo: Optional[ctk.CTkImage] = None

        self._build()
        self._refresh_qr()

    # â”€â”€ Layout â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    def _build(self):
        self.grid_columnconfigure((0, 1), weight=1, uniform="col")
        self.grid_rowconfigure(0, weight=1)

        # â”€â”€ LEFT: Status â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        left = ctk.CTkFrame(self, fg_color=CARD, corner_radius=12,
                            border_width=1, border_color=BORDER)
        left.grid(row=0, column=0, sticky="nsew", padx=(16, 8), pady=16)
        left.grid_columnconfigure(0, weight=1)

        ctk.CTkLabel(left, text="SERVER STATUS", font=("Consolas", 11, "bold"),
                     text_color=ACCENT).grid(row=0, column=0, pady=(18, 4))

        # Big status dot + label
        self._dot_canvas = tk.Canvas(left, width=20, height=20, bg=CARD,
                                     highlightthickness=0)
        self._dot_canvas.grid(row=1, column=0, pady=(10, 0))
        self._dot = self._dot_canvas.create_oval(2, 2, 18, 18, fill=ERROR, outline="")

        self._status_label = ctk.CTkLabel(
            left, text="SERVER STOPPED",
            font=("Consolas", 18, "bold"), text_color=ERROR
        )
        self._status_label.grid(row=2, column=0, pady=(4, 20))

        # Toggle button
        self._toggle_btn = ctk.CTkButton(
            left, text="START SERVER",
            font=("Consolas", 13, "bold"),
            fg_color=ACCENT, hover_color="#0066CC",
            corner_radius=8, height=42,
            command=self._toggle_server
        )
        self._toggle_btn.grid(row=3, column=0, padx=24, pady=(0, 20), sticky="ew")

        # Info rows
        sep = ctk.CTkFrame(left, height=1, fg_color=BORDER)
        sep.grid(row=4, column=0, sticky="ew", padx=16, pady=8)

        self._info_frame = ctk.CTkFrame(left, fg_color="transparent")
        self._info_frame.grid(row=5, column=0, sticky="ew", padx=24, pady=4)
        self._info_frame.grid_columnconfigure((0, 1), weight=1)

        self._ip_val   = self._info_row(self._info_frame, 0, "IP Address", self._local_ip)
        self._port_val = self._info_row(self._info_frame, 1, "Port",
                                        str(self._server.port))
        self._dev_val  = self._info_row(self._info_frame, 2, "Devices", "0")
        self._cip_val  = self._info_row(self._info_frame, 3, "Client IP", "-")
        self._prof_val = self._info_row(self._info_frame, 4, "Profile", "-")

        # ---- RIGHT: QR Code --------------------------------------------------
        right = ctk.CTkFrame(self, fg_color=CARD, corner_radius=12,
                             border_width=1, border_color=BORDER)
        right.grid(row=0, column=1, sticky="nsew", padx=(8, 16), pady=16)
        right.grid_columnconfigure(0, weight=1)

        ctk.CTkLabel(right, text="QR CODE", font=("Consolas", 11, "bold"),
                     text_color=ACCENT).grid(row=0, column=0, pady=(18, 8))

        self._qr_label = ctk.CTkLabel(right, text="", image=None)
        self._qr_label.grid(row=1, column=0, pady=4)

        ctk.CTkButton(
            right, text="Regenerate",
            font=("Consolas", 11), fg_color="#1A2535",
            hover_color="#223044", corner_radius=6, height=32,
            command=self._refresh_qr
        ).grid(row=2, column=0, padx=24, pady=8)

        ctk.CTkLabel(
            right, text="Scan with Consensius Controller app",
            font=("Consolas", 10), text_color=TEXT2
        ).grid(row=3, column=0, padx=16, pady=(0, 8))

        uri_text = f"consensius://{self._local_ip}:{self._server.port}"
        self._uri_label = ctk.CTkLabel(
            right, text=uri_text,
            font=("Consolas", 9), text_color=ACCENT
        )
        self._uri_label.grid(row=4, column=0, padx=16, pady=(0, 18))

    def _info_row(self, parent, row, label, value):
        ctk.CTkLabel(parent, text=label, font=("Consolas", 11),
                     text_color=TEXT2, anchor="w").grid(
                         row=row, column=0, sticky="w", pady=3)
        val_lbl = ctk.CTkLabel(parent, text=value, font=("Consolas", 11, "bold"),
                                text_color=TEXT, anchor="e")
        val_lbl.grid(row=row, column=1, sticky="e", pady=3)
        return val_lbl

    # ---- QR ------------------------------------------------------------------

    def _refresh_qr(self):
        img = generate_qr_image(self._local_ip, self._server.port, size=190)
        if img:
            self._qr_photo = ctk.CTkImage(light_image=img, dark_image=img, size=(190, 190))
            self._qr_label.configure(image=self._qr_photo, text="")
        else:
            self._qr_label.configure(text="qrcode library\nnot installed", text_color=TEXT2)
        uri = f"consensius://{self._local_ip}:{self._server.port}"
        self._uri_label.configure(text=uri)

    # ---- Server toggle -------------------------------------------------------

    def _toggle_server(self):
        if self._server.is_running:
            self._on_stop()
        else:
            self._on_start()

    # ---- Update helpers (called from main thread via after()) ----------------

    def set_running(self, running: bool):
        if running:
            self._dot_canvas.itemconfig(self._dot, fill=SUCCESS)
            self._status_label.configure(text="SERVER RUNNING", text_color=SUCCESS)
            self._toggle_btn.configure(text="STOP SERVER", fg_color=ERROR,
                                       hover_color="#CC1133")
        else:
            self._dot_canvas.itemconfig(self._dot, fill=ERROR)
            self._status_label.configure(text="SERVER STOPPED", text_color=ERROR)
            self._toggle_btn.configure(text="START SERVER", fg_color=ACCENT,
                                       hover_color="#0066CC")
        self._port_val.configure(text=str(self._server.port))

    def set_client(self, ip: Optional[str], profile_name: Optional[str]):
        self._cip_val.configure(text=ip or "-")
        self._prof_val.configure(text=profile_name or "-")

    def set_device_count(self, n: int):
        self._dev_val.configure(text=str(n))
