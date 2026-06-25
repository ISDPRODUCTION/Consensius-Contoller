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
        self._active_keys: Dict[str, str] = {}   # key â†’ type
        self._badge_widgets: Dict[str, ctk.CTkFrame] = {}
        self._build()

    # â”€â”€ Layout â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    def _build(self):
        self.grid_columnconfigure(0, weight=1)
        self.grid_rowconfigure(1, weight=1)
        self.grid_rowconfigure(2, weight=0)

        # â”€â”€ Header bar â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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

        # â”€â”€ Log area â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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

        # â”€â”€ Active keys section â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        keys_outer = ctk.CTkFrame(self, fg_color=CARD, corner_radius=10,
                                  border_width=1, border_color=BORDER)
        keys_outer.grid(row=2, column=0, sticky="ew", padx=16, pady=(4, 14))
        keys_outer.grid_columnconfigure(1, weight=1)

        ctk.CTkLabel(keys_outer, text="HELD KEYS",
                     font=("Consolas", 10, "bold"), text_color=TEXT2).grid(
                         row=0, column=0, padx=12, pady=8, sticky="w")

        self._keys_frame = ctk.CTkFrame(keys_outer, fg_color="transparent")
        self._keys_frame.grid(row=0, column=1, sticky="ew", padx=(0, 12), pady=6)

    # â”€â”€ Public API â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    def add_log(self, message: str, log_type: str = "system"):
        """Thread-safe log entry â€” must be called via after() from bg thread."""
        ts    = datetime.now().strftime("%H:%M:%S")
        label = TYPE_LABELS.get(log_type, "SYSTEM ")
        color_tag = f"type_{log_type}" if log_type in TYPE_COLORS else "type_system"

        self._log_text.configure(state="normal")

        # Trim if too long
        line_count = int(self._log_text.index("end-1c").split(".")[0])
        if line_count > MAX_LOG_LINES:
            self._log_text.delete("1.0", f"{line_count - MAX_LOG_LINES}.0")

        self._log_text.insert("end", f"[{ts}] ", "ts")
        self._log_text.insert("end", f"{label}  ", color_tag)
        self._log_text.insert("end", f"{message}\n", "msg")
        self._log_text.configure(state="disabled")
        self._log_text.see("end")

    def clear_log(self):
        self._log_text.configure(state="normal")
        self._log_text.delete("1.0", "end")
        self._log_text.configure(state="disabled")

    def set_key_down(self, key: str, log_type: str = "button"):
        """Mark a key as held â€” show badge."""
        self._active_keys[key] = log_type
        self._rebuild_badges()

    def set_key_up(self, key: str):
        """Remove a held key badge."""
        self._active_keys.pop(key, None)
        self._rebuild_badges()

    def clear_keys(self):
        self._active_keys.clear()
        self._rebuild_badges()

    # â”€â”€ Internal â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    def _rebuild_badges(self):
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

