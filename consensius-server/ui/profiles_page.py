# -*- coding: utf-8 -*-
"""
ui/profiles_page.py
===================
Profiles page: displays the profile received from the Android app.
"""
import customtkinter as ctk
from typing import Optional, Dict, Any


def dim_color(hex_color: str, factor: float = 0.15) -> str:
    """Blend hex_color toward black. Returns valid 6-digit hex for tkinter."""
    hex_color = hex_color.lstrip("#")
    r = int(int(hex_color[0:2], 16) * factor)
    g = int(int(hex_color[2:4], 16) * factor)
    b = int(int(hex_color[4:6], 16) * factor)
    return f"#{r:02X}{g:02X}{b:02X}"

BG      = "#0A0A0F"
CARD    = "#0F1624"
ACCENT  = "#0084FF"
SUCCESS = "#00E676"
TEXT    = "#E0E0E0"
TEXT2   = "#8899AA"
BORDER  = "#1A2535"
BLUE    = "#0084FF"


class ProfilesPage(ctk.CTkFrame):
    def __init__(self, master, **kwargs):
        super().__init__(master, fg_color=BG, **kwargs)
        self._profile: Optional[Dict[str, Any]] = None
        self._build()

    def _build(self):
        self.grid_columnconfigure(0, weight=1)
        self.grid_rowconfigure(1, weight=1)

        ctk.CTkLabel(self, text="ACTIVE PROFILE", font=("Consolas", 11, "bold"),
                     text_color=ACCENT).grid(row=0, column=0, sticky="w",
                                             padx=20, pady=(16, 6))

        self._content = ctk.CTkFrame(self, fg_color="transparent")
        self._content.grid(row=1, column=0, sticky="nsew", padx=16, pady=8)
        self._content.grid_columnconfigure(0, weight=1)
        self._content.grid_rowconfigure(0, weight=1)

        self._show_empty()

    # â”€â”€ Public â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    def set_profile(self, profile_data: Dict[str, Any]):
        self._profile = profile_data
        self._show_profile()

    def clear(self):
        self._profile = None
        self._show_empty()

    # â”€â”€ Rendering â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    def _clear_content(self):
        for w in self._content.winfo_children():
            w.destroy()

    def _show_empty(self):
        self._clear_content()
        frame = ctk.CTkFrame(self._content, fg_color=CARD, corner_radius=12,
                             border_width=1, border_color=BORDER)
        frame.grid(row=0, column=0, sticky="nsew", padx=8, pady=8)
        frame.grid_columnconfigure(0, weight=1)
        frame.grid_rowconfigure(0, weight=1)
        ctk.CTkLabel(frame, text="No profile received yet",
                     font=("Consolas", 14), text_color=TEXT2).grid(
                         row=0, column=0, pady=40)
        ctk.CTkLabel(frame,
                     text="Connect an Android device and it will\nautomatically sync its profile here.",
                     font=("Consolas", 10), text_color=TEXT2, justify="center").grid(
                         row=1, column=0, pady=(0, 40))

    def _show_profile(self):
        self._clear_content()
        if not self._profile:
            return

        p = self._profile
        name     = p.get("name", "Unknown")
        rj_mode  = p.get("rightJoystickMode", "-")
        dpad     = p.get("dpadMapping", {})
        buttons  = p.get("buttons", [])

        # Scrollable container
        scroll = ctk.CTkScrollableFrame(self._content, fg_color="transparent",
                                        scrollbar_button_color=ACCENT)
        scroll.grid(row=0, column=0, sticky="nsew")
        scroll.grid_columnconfigure((0, 1), weight=1, uniform="col")

        # Profile name header
        header = ctk.CTkFrame(scroll, fg_color=CARD, corner_radius=10,
                              border_width=1, border_color="#1A3050")
        header.grid(row=0, column=0, columnspan=2, sticky="ew", padx=8, pady=(4, 8))
        header.grid_columnconfigure(0, weight=1)

        ctk.CTkLabel(header, text=name, font=("Consolas", 18, "bold"),
                     text_color=TEXT).grid(row=0, column=0, padx=16, pady=(14, 2))
        ctk.CTkLabel(header, text=f"Right Stick: {rj_mode}",
                     font=("Consolas", 11), text_color=ACCENT).grid(
                         row=1, column=0, padx=16, pady=(0, 12))

        # D-Pad card
        dpad_card = self._section_card(scroll, row=1, col=0, title="D-PAD MAPPING")
        dirs = [("Up",    dpad.get("up", "-")),   ("Down",  dpad.get("down", "-")),
                ("Left",  dpad.get("left", "-")), ("Right", dpad.get("right", "-"))]
        for i, (label, key) in enumerate(dirs):
            self._key_row(dpad_card, i + 1, label, key, "#00C2FF")

        # Buttons card
        btn_card = self._section_card(scroll, row=1, col=1, title="BUTTONS")
        for i, btn in enumerate(buttons):
            lbl  = btn.get("label", "?")
            key  = btn.get("key", "?")
            size = btn.get("size", "M")
            self._key_row(btn_card, i + 1, f"{lbl}  [{size}]", key, BLUE)

    def _section_card(self, parent, row, col, title):
        card = ctk.CTkFrame(parent, fg_color=CARD, corner_radius=10,
                            border_width=1, border_color=BORDER)
        card.grid(row=row, column=col, sticky="nsew", padx=8, pady=4)
        card.grid_columnconfigure((0, 1), weight=1)
        ctk.CTkLabel(card, text=title, font=("Consolas", 10, "bold"),
                     text_color=ACCENT).grid(row=0, column=0, columnspan=2,
                                             padx=12, pady=(10, 4), sticky="w")
        sep = ctk.CTkFrame(card, height=1, fg_color=BORDER)
        sep.grid(row=0, column=0, columnspan=2, sticky="ew",
                 padx=12, pady=(32, 0))
        return card

    def _key_row(self, card, row, label, key, color):
        ctk.CTkLabel(card, text=label, font=("Consolas", 11), text_color=TEXT2,
                     anchor="w").grid(row=row, column=0, sticky="w", padx=12, pady=3)
        badge = ctk.CTkFrame(card, fg_color=dim_color(color, 0.15), corner_radius=4)
        badge.grid(row=row, column=1, sticky="e", padx=12, pady=3)
        ctk.CTkLabel(badge, text=f" {key} ", font=("Consolas", 11, "bold"),
                     text_color=color).pack(padx=4, pady=1)

