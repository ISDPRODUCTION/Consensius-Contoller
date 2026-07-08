# -*- coding: utf-8 -*-
"""
ui/monitor_page.py
==================
Realtime input monitor with color-coded log entries and active-keys badges.
"""
import tkinter as tk
import customtkinter as ctk
from datetime import datetime
from typing import Dict, Set


def dim_color(hex_color: str, factor: float = 0.15) -> str:
    """Blend hex_color toward black by factor (0=black, 1=original). Returns 6-digit hex."""
    hex_color = hex_color.lstrip("#")
    r = int(hex_color[0:2], 16)
    g = int(hex_color[2:4], 16)
    b = int(hex_color[4:6], 16)
    r = int(r * factor)
    g = int(g * factor)
    b = int(b * factor)
    return f"#{r:02X}{g:02X}{b:02X}"

BG      = "#0A0A0F"
CARD    = "#0F1624"
SURFACE = "#0D1117"
ACCENT  = "#0084FF"
TEXT    = "#E0E0E0"
TEXT2   = "#8899AA"
BORDER  = "#1A2535"

# Color coding per type
TYPE_COLORS = {
    "button":   "#0084FF",   # blue
    "joystick": "#00E676",   # green
    "mouse":    "#00C2FF",   # cyan
    "system":   "#8899AA",   # gray
}
TYPE_LABELS = {
    "button":   "BUTTON ",
    "joystick": "JOYSTCK",
    "mouse":    "MOUSE  ",
    "system":   "SYSTEM ",
}

MAX_LOG_LINES = 500


class MonitorPage(ctk.CTkFrame):
    def __init__(self, master, **kwargs):
        super().__init__(master, fg_color=BG, **kwargs)
        self._active_keys: Dict[str, str] = {}   # key → type
        self._badge_widgets: Dict[str, ctk.CTkFrame] = {}
        # Fix #2: Track last-rendered key set to skip unnecessary rebuilds
        self._last_badge_keys: tuple = ()   # tuple of (key, type) pairs
        self._build()

    # ──────────────────────────────────────────────────────────────────────────────────

    def _build(self):
        self.grid_columnconfigure(0, weight=1)
        self.grid_rowconfigure(1, weight=1)
        self.grid_rowconfigure(2, weight=0)

        # ── Header bar ────────────────────────────────────────────────────────────────────
        header = ctk.CTkFrame(self, fg_color="transparent")
        header.grid(row=0, column=0, sticky="ew", padx=16, pady=(14, 6))
        header.grid_columnconfigure(0, weight=1)

        ctk.CTkLabel(header, text="INPUT MONITOR",
                     font=("Consolas", 11, "bold"), text_color=ACCENT).grid(
                         row=0, column=0, sticky="w")

        ctk.CTkButton(
            header, text="CLEAR", font=("Consolas", 10, "bold"),
            fg_color="#1A2535", hover_color="#223044",
            corner_radius=6, height=26, width=70,
            command=self.clear_log
        ).grid(row=0, column=1, sticky="e")

        # Legend chips
        legend = ctk.CTkFrame(header, fg_color="transparent")
        legend.grid(row=1, column=0, columnspan=2, sticky="w", pady=(4, 0))
        for i, (t, color) in enumerate(TYPE_COLORS.items()):
            chip = ctk.CTkFrame(legend, fg_color=dim_color(color, 0.15), corner_radius=4)
            chip.grid(row=0, column=i, padx=(0, 6))
            ctk.CTkLabel(chip, text=TYPE_LABELS[t].strip(),
                         font=("Consolas", 9, "bold"),
                         text_color=color).pack(padx=6, pady=2)

        # ── Log area ──────────────────────────────────────────────────────────────────────
        log_frame = ctk.CTkFrame(self, fg_color=SURFACE, corner_radius=10,
                                 border_width=1, border_color=BORDER)
        log_frame.grid(row=1, column=0, sticky="nsew", padx=16, pady=4)

        self._log_text = tk.Text(
            log_frame,
            bg=SURFACE, fg=TEXT,
            font=("Consolas", 11),
            state="disabled",
            wrap="none",
            bd=0, relief="flat",
            highlightthickness=0,
            padx=10, pady=8,
            insertbackground=ACCENT
        )
        self._log_text.pack(side="left", fill="both", expand=True)

        scrollbar = ctk.CTkScrollbar(log_frame, command=self._log_text.yview)
        scrollbar.pack(side="right", fill="y")
        self._log_text.configure(yscrollcommand=scrollbar.set)

        # Configure color tags
        for t, color in TYPE_COLORS.items():
            self._log_text.tag_configure(f"type_{t}", foreground=color)
        self._log_text.tag_configure("ts", foreground=TEXT2)
        self._log_text.tag_configure("msg", foreground=TEXT)

        # ── Active keys section ──────────────────────────────────────────────────────────
        keys_outer = ctk.CTkFrame(self, fg_color=CARD, corner_radius=10,
                                  border_width=1, border_color=BORDER)
        keys_outer.grid(row=2, column=0, sticky="ew", padx=16, pady=(4, 14))
        keys_outer.grid_columnconfigure(1, weight=1)

        ctk.CTkLabel(keys_outer, text="HELD KEYS",
                     font=("Consolas", 10, "bold"), text_color=TEXT2).grid(
                         row=0, column=0, padx=12, pady=8, sticky="w")

        self._keys_frame = ctk.CTkFrame(keys_outer, fg_color="transparent")
        self._keys_frame.grid(row=0, column=1, sticky="ew", padx=(0, 12), pady=6)

    # ── Public API ──────────────────────────────────────────────────────────────────

    def add_log(self, message: str, log_type: str = "system"):
        """Single-entry log — used for direct calls. Delegates to batch method."""
        self.add_log_batch([(message, log_type)])

    def add_log_batch(self, entries: list):
        """Fix #3: Batch-insert multiple log entries into the Text widget in one pass.
        
        Instead of querying index(), inserting, and calling see('end') for every
        single message (which was happening 60–200×/s), we:
          1. Enable editing once
          2. Trim excess lines once (if needed)
          3. Build a buffer and insert all entries in one call
          4. Call see('end') once at the end
          5. Disable editing once
        """
        if not entries:
            return

        self._log_text.configure(state="normal")

        # Trim excess lines once before inserting the batch
        line_count = int(self._log_text.index("end-1c").split(".")[0])
        new_total = line_count + len(entries)
        if new_total > MAX_LOG_LINES:
            excess = new_total - MAX_LOG_LINES
            self._log_text.delete("1.0", f"{excess + 1}.0")

        # Insert all entries in one editing session
        ts_now = datetime.now().strftime("%H:%M:%S")
        for message, log_type in entries:
            label     = TYPE_LABELS.get(log_type, "SYSTEM ")
            color_tag = f"type_{log_type}" if log_type in TYPE_COLORS else "type_system"
            self._log_text.insert("end", f"[{ts_now}] ", "ts")
            self._log_text.insert("end", f"{label}  ", color_tag)
            self._log_text.insert("end", f"{message}\n", "msg")

        self._log_text.configure(state="disabled")
        self._log_text.see("end")   # scroll once for the whole batch

    def clear_log(self):
        self._log_text.configure(state="normal")
        self._log_text.delete("1.0", "end")
        self._log_text.configure(state="disabled")

    def set_key_down(self, key: str, log_type: str = "button"):
        """Mark a key as held — show badge."""
        self._active_keys[key] = log_type
        self._rebuild_badges()

    def set_key_up(self, key: str):
        """Remove a held key badge."""
        self._active_keys.pop(key, None)
        self._rebuild_badges()

    def clear_keys(self):
        self._active_keys.clear()
        self._rebuild_badges()

    # ── Internal ─────────────────────────────────────────────────────────────

    def _rebuild_badges(self):
        # Fix #2: Skip rebuild if the key set hasn’t actually changed.
        # When a button is held during rapid joystick spam, set_key_down/up can
        # be called many times with the same state — this check prevents
        # unnecessary destroy/recreate cycles.
        current_snapshot = tuple(self._active_keys.items())
        if current_snapshot == self._last_badge_keys:
            return
        self._last_badge_keys = current_snapshot

        for w in self._keys_frame.winfo_children():
            w.destroy()
        for i, (key, t) in enumerate(self._active_keys.items()):
            color = TYPE_COLORS.get(t, TEXT2)
            badge = ctk.CTkFrame(self._keys_frame, fg_color=dim_color(color, 0.2),
                                 corner_radius=6, border_width=1,
                                 border_color=color)
            badge.grid(row=0, column=i, padx=3)
            ctk.CTkLabel(badge, text=f" {key} ",
                         font=("Consolas", 11, "bold"),
                         text_color=color).pack(padx=4, pady=3)

