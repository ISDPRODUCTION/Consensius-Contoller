# -*- coding: utf-8 -*-
"""
ui/app_window.py
================
Main application window with dark sidebar navigation.
Hosts all pages and wires the server + input handler callbacks.
"""
import tkinter as tk
import threading
import customtkinter as ctk
from typing import Dict, Optional

from ui.connection_page import ConnectionPage
from ui.profiles_page   import ProfilesPage
from ui.monitor_page    import MonitorPage
from ui.settings_page   import SettingsPage
from ui.about_page      import AboutPage

# ── Palette ────────────────────────────────────────────────────────────────────
BG        = "#0A0A0F"
SIDEBAR   = "#0D1117"
ACCENT    = "#0084FF"
TEXT      = "#E0E0E0"
TEXT2     = "#8899AA"
BORDER    = "#1A2535"
SUCCESS   = "#00E676"
ERROR     = "#FF1744"

NAV_ITEMS = [
    ("connection", "Connection"),
    ("profiles",   "Profiles"),
    ("monitor",    "Monitor"),
    ("settings",   "Settings"),
    ("about",      "About"),
]


class AppWindow(ctk.CTk):
    def __init__(self, server, input_handler, settings: dict):
        super().__init__()

        self._server        = server
        self._input_handler = input_handler
        self._settings      = settings
        self._active_page   = "connection"
        self._nav_buttons: Dict[str, ctk.CTkButton] = {}

        # ── Fix #1: Log batch buffer ─────────────────────────────────────────
        # Instead of scheduling self.after(0, ...) for EVERY incoming message
        # (which floods the Tkinter event queue at 60–200 calls/s and causes
        # the UI to lag more and more over time), we accumulate messages in a
        # list and flush them all at once every LOG_FLUSH_MS milliseconds.
        self._log_batch: list = []          # [(message, log_type), ...]
        self._key_batch: list = []          # [("down"|"up", key, log_type), ...]
        self._batch_flush_pending: bool = False
        self._LOG_FLUSH_MS: int = 30        # flush UI at ~33 fps

        # Window setup
        ctk.set_appearance_mode("dark")
        ctk.set_default_color_theme("blue")
        self.title("Consensius Server")
        self.geometry("900x600")
        self.minsize(800, 520)
        self.configure(fg_color=BG)
        self.protocol("WM_DELETE_WINDOW", self._on_close)

        self._build()
        self._wire_server_callbacks()

        # Auto-start if configured
        if settings.get("start_on_launch", True):
            self.after(500, self._start_server)

    # ── Layout ─────────────────────────────────────────────────────────────────

    def _build(self):
        self.grid_columnconfigure(1, weight=1)
        self.grid_rowconfigure(0, weight=1)

        # ── Sidebar ─────────────────────────────────────────────────────────
        sidebar = ctk.CTkFrame(self, fg_color=SIDEBAR, corner_radius=0,
                               border_width=1, border_color=BORDER)
        sidebar.grid(row=0, column=0, sticky="nsew")
        sidebar.grid_rowconfigure(len(NAV_ITEMS) + 1, weight=1)

        # Logo
        logo_frame = ctk.CTkFrame(sidebar, fg_color="transparent")
        logo_frame.grid(row=0, column=0, pady=(20, 16), padx=14)

        logo_canvas = tk.Canvas(logo_frame, width=36, height=36,
                                bg=SIDEBAR, highlightthickness=0)
        logo_canvas.pack(side="left")
        logo_canvas.create_oval(2, 2, 34, 34, fill=ACCENT, outline="")
        logo_canvas.create_text(18, 18, text="CC", fill="white",
                                font=("Consolas", 11, "bold"))

        ctk.CTkLabel(logo_frame, text=" Consensius\n Server",
                     font=("Consolas", 11, "bold"), text_color=TEXT,
                     justify="left").pack(side="left")

        sep = ctk.CTkFrame(sidebar, height=1, fg_color=BORDER)
        sep.grid(row=1, column=0, sticky="ew", padx=10, pady=(0, 8))

        # Nav buttons
        for i, (page_id, label) in enumerate(NAV_ITEMS):
            btn = ctk.CTkButton(
                sidebar, text=label,
                font=("Consolas", 11),
                anchor="w",
                fg_color="transparent",
                hover_color="#132030",
                text_color=TEXT2,
                corner_radius=8,
                height=38,
                command=lambda pid=page_id: self._show_page(pid)
            )
            btn.grid(row=i + 2, column=0, sticky="ew", padx=8, pady=2)
            self._nav_buttons[page_id] = btn

        # Status chip at bottom of sidebar
        self._status_chip = ctk.CTkLabel(
            sidebar, text="[STOPPED]",
            font=("Consolas", 10, "bold"), text_color=ERROR
        )
        self._status_chip.grid(row=len(NAV_ITEMS) + 2, column=0,
                               pady=(0, 16), padx=12, sticky="s")

        # ── Page container ───────────────────────────────────────────────────
        self._page_frame = ctk.CTkFrame(self, fg_color=BG, corner_radius=0)
        self._page_frame.grid(row=0, column=1, sticky="nsew")
        self._page_frame.grid_columnconfigure(0, weight=1)
        self._page_frame.grid_rowconfigure(0, weight=1)

        # Build all pages
        self._pages: Dict[str, ctk.CTkFrame] = {}

        self._pages["connection"] = ConnectionPage(
            self._page_frame, self._server,
            on_start=self._start_server,
            on_stop=self._stop_server
        )
        self._pages["profiles"]   = ProfilesPage(self._page_frame)
        self._pages["monitor"]    = MonitorPage(self._page_frame)
        self._pages["settings"]   = SettingsPage(
            self._page_frame, self._settings,
            on_save=self._on_settings_saved
        )
        self._pages["about"]      = AboutPage(self._page_frame)

        for page in self._pages.values():
            page.grid(row=0, column=0, sticky="nsew")

        self._show_page("connection")

    # ── Navigation ─────────────────────────────────────────────────────────────

    def _show_page(self, page_id: str):
        self._active_page = page_id
        for pid, page in self._pages.items():
            if pid == page_id:
                page.tkraise()
            # Update nav button style
            btn = self._nav_buttons.get(pid)
            if btn:
                if pid == page_id:
                    btn.configure(fg_color="#132030", text_color=ACCENT)
                else:
                    btn.configure(fg_color="transparent", text_color=TEXT2)

    # ── Server control ─────────────────────────────────────────────────────────

    def _start_server(self):
        """
        Bug 4 fix: Run server start in a background thread so the UI thread
        is never blocked. Status is refreshed after a short delay.
        """
        def _run():
            self._server.start_in_thread()
            self.after(400, self._refresh_status)

        threading.Thread(target=_run, daemon=True).start()

    def _stop_server(self):
        """
        Bug 4 fix: Run server stop in a background thread.
        """
        def _run():
            self._server.stop()
            self.after(0, self._refresh_status)

        threading.Thread(target=_run, daemon=True).start()

    def _refresh_status(self):
        running = self._server.is_running
        conn_page: ConnectionPage = self._pages["connection"]  # type: ignore
        conn_page.set_running(running)
        conn_page.set_device_count(self._server.connected_count)
        if running:
            self._status_chip.configure(text="[RUNNING]", text_color=SUCCESS)
        else:
            self._status_chip.configure(text="[STOPPED]", text_color=ERROR)

    # ── Server callbacks (called from background thread → schedule on main) ────

    def _wire_server_callbacks(self):
        monitor: MonitorPage = self._pages["monitor"]  # type: ignore

        def _on_log(message: str, log_type: str):
            # Fix #1: Append to batch instead of scheduling individual after(0) calls.
            # The _flush_log_batch() timer will drain this list every LOG_FLUSH_MS ms.
            self._log_batch.append((message, log_type))

            # Button key-badge events are rare and important — queue them too
            # so they get processed in the same flush pass.
            if log_type == "button":
                if "\u2192 DOWN" in message:
                    key = message.split("\u2192")[0].strip()
                    self._key_batch.append(("down", key, log_type))
                elif "\u2192 UP" in message:
                    key = message.split("\u2192")[0].strip()
                    self._key_batch.append(("up", key, log_type))

            # Schedule a single flush callback if none is pending yet.
            if not self._batch_flush_pending:
                self._batch_flush_pending = True
                self.after(self._LOG_FLUSH_MS, self._flush_log_batch)

        def _on_connect(ip: str):
            self.after(0, self._on_client_connect, ip)

        def _on_disconnect():
            self.after(0, self._on_client_disconnect)

        def _on_profile(profile_data: dict):
            self.after(0, self._on_profile_received, profile_data)

        self._server.on_log        = _on_log
        self._server.on_connect    = _on_connect
        self._server.on_disconnect = _on_disconnect
        self._server.on_profile    = _on_profile

    def _flush_log_batch(self) -> None:
        """Fix #1: Drain the log batch and apply all pending UI updates at once.
        Called by Tkinter's after() timer every LOG_FLUSH_MS ms.
        Runs on the main thread — safe to touch all widgets directly.
        """
        self._batch_flush_pending = False
        monitor: MonitorPage = self._pages["monitor"]  # type: ignore

        # Drain log lines — batch insert into the Text widget
        if self._log_batch:
            batch, self._log_batch = self._log_batch, []
            monitor.add_log_batch(batch)

        # Drain key badge updates
        if self._key_batch:
            batch, self._key_batch = self._key_batch, []
            for direction, key, log_type in batch:
                if direction == "down":
                    monitor.set_key_down(key, log_type)
                else:
                    monitor.set_key_up(key)

    def _on_client_connect(self, ip: str):
        conn_page: ConnectionPage = self._pages["connection"]  # type: ignore
        conn_page.set_client(ip, None)
        conn_page.set_device_count(self._server.connected_count)

    def _on_client_disconnect(self):
        conn_page: ConnectionPage = self._pages["connection"]  # type: ignore
        conn_page.set_client(None, None)
        conn_page.set_device_count(self._server.connected_count)
        monitor: MonitorPage = self._pages["monitor"]  # type: ignore
        monitor.clear_keys()

    def _on_profile_received(self, profile_data: dict):
        profiles_page: ProfilesPage = self._pages["profiles"]  # type: ignore
        profiles_page.set_profile(profile_data)
        name = profile_data.get("name", "Unknown")
        conn_page: ConnectionPage = self._pages["connection"]  # type: ignore
        conn_page.set_client(self._server.client_ip, name)

    # ── Settings ───────────────────────────────────────────────────────────────

    def _on_settings_saved(self, new_settings: dict):
        self._settings = new_settings
        self._server.update_settings(new_settings)
        self._input_handler.update_settings(new_settings)

    # ── Close ──────────────────────────────────────────────────────────────────

    def _on_close(self):
        self._server.stop()
        self.destroy()
