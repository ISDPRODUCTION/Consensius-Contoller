# -*- coding: utf-8 -*-
"""
ui/settings_page.py
===================
Settings page: port, start-on-launch, mouse sensitivity, joystick threshold,
and Skill Aim Positions (capture the screen position of each skill button).
"""
import json
import time
import threading
import customtkinter as ctk
from pathlib import Path
from typing import Callable

try:
    from pynput.mouse import Controller as MouseCtrl
    _mouse_ctrl = MouseCtrl()
    PYNPUT_OK = True
except Exception:
    PYNPUT_OK = False

BG      = "#0A0A0F"
CARD    = "#0F1624"
ACCENT  = "#0084FF"
SUCCESS = "#00E676"
TEXT    = "#E0E0E0"
TEXT2   = "#8899AA"
BORDER  = "#1A2535"
ERROR   = "#FF1744"
WARN    = "#FFB300"

SETTINGS_FILE = Path(__file__).resolve().parent.parent / "settings.json"

# Default skill keys shown in the Skill Aim Positions panel
DEFAULT_SKILL_KEYS = ["j", "k", "l", "u", "i", "f"]


class SettingsPage(ctk.CTkFrame):
    def __init__(self, master, settings: dict, on_save: Callable[[dict], None], **kwargs):
        super().__init__(master, fg_color=BG, **kwargs)
        self._settings = dict(settings)
        self._on_save  = on_save
        # Per-key StringVars for x,y coordinate display
        self._pos_vars: dict = {}   # key -> {"x": StringVar, "y": StringVar}
        self._build()

    # ── Layout ─────────────────────────────────────────────────────────────────

    def _build(self):
        self.grid_columnconfigure(0, weight=1)
        self.grid_rowconfigure(1, weight=1)

        ctk.CTkLabel(self, text="SETTINGS", font=("Consolas", 11, "bold"),
                     text_color=ACCENT).grid(row=0, column=0, sticky="w",
                                             padx=20, pady=(16, 6))

        # Scrollable container so all settings fit
        scroll = ctk.CTkScrollableFrame(self, fg_color=BG, scrollbar_button_color=BORDER)
        scroll.grid(row=1, column=0, sticky="nsew", padx=0, pady=0)
        scroll.grid_columnconfigure(0, weight=1)

        content = ctk.CTkFrame(scroll, fg_color=CARD, corner_radius=12,
                               border_width=1, border_color=BORDER)
        content.grid(row=0, column=0, sticky="ew", padx=16, pady=8)
        content.grid_columnconfigure(0, weight=1)

        row = 0

        # ── Server section ───────────────────────────────────────────────────
        self._section(content, row, "SERVER"); row += 1

        # Port
        port_frame = self._row_frame(content, row); row += 1
        ctk.CTkLabel(port_frame, text="Port", font=("Consolas", 12),
                     text_color=TEXT, anchor="w").pack(side="left", padx=12)
        self._port_var = ctk.StringVar(value=str(self._settings.get("port", 8765)))
        ctk.CTkEntry(port_frame, textvariable=self._port_var,
                     width=80, font=("Consolas", 12),
                     fg_color="#1A2535", border_color=ACCENT,
                     text_color=TEXT).pack(side="right", padx=12, pady=6)

        # Start on launch toggle
        sol_frame = self._row_frame(content, row); row += 1
        ctk.CTkLabel(sol_frame, text="Start server on launch",
                     font=("Consolas", 12), text_color=TEXT,
                     anchor="w").pack(side="left", padx=12)
        self._sol_var = ctk.BooleanVar(
            value=self._settings.get("start_on_launch", True))
        ctk.CTkSwitch(sol_frame, text="", variable=self._sol_var,
                      progress_color=ACCENT, button_color=TEXT).pack(
                          side="right", padx=12, pady=6)

        # ── Controller section ───────────────────────────────────────────────
        self._section(content, row, "CONTROLLER"); row += 1

        # Mouse sensitivity
        ms_val = self._settings.get("mouse_sensitivity", 1.5)
        self._ms_var = ctk.DoubleVar(value=ms_val)
        row = self._slider_row(content, row, "Mouse Sensitivity",
                               self._ms_var, 0.5, 3.0, "×")

        # Joystick threshold
        jt_val = self._settings.get("joystick_threshold", 0.3)
        self._jt_var = ctk.DoubleVar(value=jt_val)
        row = self._slider_row(content, row, "Joystick Threshold (WASD)",
                               self._jt_var, 0.1, 0.5, "")

        # Skill aim distance
        sad_val = self._settings.get("skill_aim_distance", 300)
        self._sad_var = ctk.DoubleVar(value=float(sad_val))
        row = self._slider_row(content, row, "Skill Aim Range (px)",
                               self._sad_var, 50, 800, " px")

        # ── Skill Aim Positions section ───────────────────────────────────────
        self._section(content, row, "SKILL AIM POSITIONS"); row += 1

        # Hint label
        hint_frame = ctk.CTkFrame(content, fg_color="transparent")
        hint_frame.grid(row=row, column=0, sticky="ew", padx=12, pady=(0, 6)); row += 1
        ctk.CTkLabel(
            hint_frame,
            text=(
                "[!] Click [CAPTURE] -> server waits 3 s -> move mouse to the skill\n"
                "    button on your game screen and hold still. Position is recorded."
            ),
            font=("Consolas", 10), text_color=TEXT2, justify="left", anchor="w"
        ).pack(side="left", padx=4)

        # Column headers
        hdr = ctk.CTkFrame(content, fg_color="transparent")
        hdr.grid(row=row, column=0, sticky="ew", padx=12, pady=(2, 0)); row += 1
        hdr.grid_columnconfigure(1, weight=1)
        for col, lbl in enumerate(["KEY", "X", "Y", ""]):
            ctk.CTkLabel(hdr, text=lbl, font=("Consolas", 10, "bold"),
                         text_color=ACCENT, width=60 if col < 3 else 90,
                         anchor="w").grid(row=0, column=col, padx=4)

        # One row per skill key
        skill_positions = self._settings.get("skill_positions", {})
        # Collect all configured keys + defaults
        all_keys = list(dict.fromkeys(DEFAULT_SKILL_KEYS + list(skill_positions.keys())))

        for key in all_keys:
            pos = skill_positions.get(key, {"x": -1, "y": -1})
            x_val = pos.get("x", -1) if isinstance(pos, dict) else -1
            y_val = pos.get("y", -1) if isinstance(pos, dict) else -1

            x_var = ctk.StringVar(value=str(int(x_val)) if x_val >= 0 else "-")
            y_var = ctk.StringVar(value=str(int(y_val)) if y_val >= 0 else "-")
            self._pos_vars[key] = {"x": x_var, "y": y_var}

            row = self._skill_pos_row(content, row, key, x_var, y_var)

        # ── Add custom key row ───────────────────────────────────────────────
        add_frame = ctk.CTkFrame(content, fg_color="transparent")
        add_frame.grid(row=row, column=0, sticky="ew", padx=12, pady=(4, 8)); row += 1
        self._new_key_var = ctk.StringVar()
        ctk.CTkEntry(add_frame, textvariable=self._new_key_var,
                     placeholder_text="key (e.g. e)", width=70,
                     font=("Consolas", 12), fg_color="#1A2535",
                     border_color=BORDER, text_color=TEXT).pack(side="left", padx=(0, 8))
        ctk.CTkButton(add_frame, text="+ Add Key", width=100,
                      font=("Consolas", 11), fg_color="#132030",
                      hover_color="#1A3040", border_width=1,
                      border_color=BORDER, text_color=TEXT2,
                      command=self._add_custom_key).pack(side="left")

        # ── Save button ───────────────────────────────────────────────────────
        self._save_status = ctk.CTkLabel(self, text="", font=("Consolas", 11),
                                          text_color=SUCCESS)
        self._save_status.grid(row=2, column=0, pady=(4, 0))

        ctk.CTkButton(
            self, text="SAVE SETTINGS",
            font=("Consolas", 13, "bold"),
            fg_color=ACCENT, hover_color="#0066CC",
            corner_radius=8, height=42,
            command=self._save
        ).grid(row=3, column=0, padx=16, pady=(4, 20), sticky="ew")

    # ── Skill position row ─────────────────────────────────────────────────────

    def _skill_pos_row(self, parent, row: int, key: str,
                       x_var: ctk.StringVar, y_var: ctk.StringVar) -> int:
        frame = ctk.CTkFrame(parent, fg_color="transparent")
        frame.grid(row=row, column=0, sticky="ew", padx=12, pady=2)

        # Key label
        ctk.CTkLabel(frame, text=f" {key} ", font=("Consolas", 12, "bold"),
                     text_color=TEXT, fg_color="#1A2535",
                     corner_radius=4, width=34).pack(side="left", padx=(0, 8))

        # X coordinate
        ctk.CTkLabel(frame, text="X:", font=("Consolas", 11),
                     text_color=TEXT2).pack(side="left")
        ctk.CTkLabel(frame, textvariable=x_var, font=("Consolas", 11),
                     text_color=SUCCESS, width=54, anchor="e").pack(side="left", padx=(2, 8))

        # Y coordinate
        ctk.CTkLabel(frame, text="Y:", font=("Consolas", 11),
                     text_color=TEXT2).pack(side="left")
        ctk.CTkLabel(frame, textvariable=y_var, font=("Consolas", 11),
                     text_color=SUCCESS, width=54, anchor="e").pack(side="left", padx=(2, 8))

        # Status label (shown during capture countdown)
        status_var = ctk.StringVar(value="")
        status_lbl = ctk.CTkLabel(frame, textvariable=status_var,
                                  font=("Consolas", 10), text_color=WARN, width=80)
        status_lbl.pack(side="left", padx=4)

        # Capture button
        cap_btn = ctk.CTkButton(
            frame, text="CAPTURE", width=100,
            font=("Consolas", 11, "bold"),
            fg_color="#132030", hover_color="#1A3040",
            border_width=1, border_color=ACCENT,
            text_color=ACCENT,
            command=lambda k=key, xv=x_var, yv=y_var, sv=status_var, btn=None: \
                self._start_capture(k, xv, yv, sv)
        )
        cap_btn.pack(side="right", padx=4)

        # Clear button
        ctk.CTkButton(
            frame, text="X", width=28,
            font=("Consolas", 11),
            fg_color="transparent", hover_color="#2A0A0A",
            border_width=1, border_color="#3A1515",
            text_color=ERROR,
            command=lambda k=key, xv=x_var, yv=y_var: self._clear_pos(k, xv, yv)
        ).pack(side="right", padx=2)

        return row + 1

    # ── Capture logic ──────────────────────────────────────────────────────────

    def _start_capture(self, key: str, x_var: ctk.StringVar,
                       y_var: ctk.StringVar, status_var: ctk.StringVar):
        """Start a 3-second countdown then read mouse position."""
        if not PYNPUT_OK:
            status_var.set("pynput N/A")
            return

        def _countdown():
            for i in range(3, 0, -1):
                self.after(0, status_var.set, f"Wait {i}s...")
                time.sleep(1)
            # Read current mouse position
            try:
                px, py = _mouse_ctrl.position
            except Exception:
                px, py = -1, -1
            self.after(0, self._apply_capture, key, x_var, y_var, status_var, px, py)

        threading.Thread(target=_countdown, daemon=True).start()
        status_var.set("Wait 3s...")

    def _apply_capture(self, key: str, x_var: ctk.StringVar,
                       y_var: ctk.StringVar, status_var: ctk.StringVar,
                       px: int, py: int):
        if px >= 0 and py >= 0:
            x_var.set(str(px))
            y_var.set(str(py))
            status_var.set("[OK] captured")
            print(f"[SKILL_POS] Captured key={key!r} -> ({px}, {py})")
        else:
            status_var.set("[WARN] failed")
        # Clear status after 3 s
        self.after(3000, status_var.set, "")

    def _clear_pos(self, key: str, x_var: ctk.StringVar, y_var: ctk.StringVar):
        x_var.set("-")
        y_var.set("-")

    # ── Add custom key ─────────────────────────────────────────────────────────

    def _add_custom_key(self):
        key = self._new_key_var.get().strip().lower()
        if not key or key in self._pos_vars:
            return
        x_var = ctk.StringVar(value="-")
        y_var  = ctk.StringVar(value="-")
        self._pos_vars[key] = {"x": x_var, "y": y_var}
        self._new_key_var.set("")
        # Need to rebuild to show new row -- simplest approach: trigger save then rebuild
        self._save_status.configure(text=f"Key '{key}' added - save to persist.", text_color=WARN)
        self.after(3000, lambda: self._save_status.configure(text=""))

    # ── Helpers ────────────────────────────────────────────────────────────────

    def _section(self, parent, row, title):
        lbl = ctk.CTkLabel(parent, text=title, font=("Consolas", 10, "bold"),
                           text_color=ACCENT)
        lbl.grid(row=row, column=0, sticky="w", padx=16, pady=(16, 2))
        sep = ctk.CTkFrame(parent, height=1, fg_color=BORDER)
        sep.grid(row=row, column=0, sticky="ew", padx=12, pady=(34, 0))

    def _row_frame(self, parent, row):
        frame = ctk.CTkFrame(parent, fg_color="transparent")
        frame.grid(row=row, column=0, sticky="ew", padx=4, pady=2)
        frame.grid_columnconfigure(0, weight=1)
        return frame

    def _slider_row(self, parent, row, label, var, from_, to, suffix):
        frame = ctk.CTkFrame(parent, fg_color="transparent")
        frame.grid(row=row, column=0, sticky="ew", padx=12, pady=4)
        frame.grid_columnconfigure(1, weight=1)

        ctk.CTkLabel(frame, text=label, font=("Consolas", 12),
                     text_color=TEXT, anchor="w").grid(row=0, column=0, sticky="w")

        val_lbl = ctk.CTkLabel(frame, text=f"{var.get():.2f}{suffix}",
                               font=("Consolas", 11, "bold"), text_color=ACCENT)
        val_lbl.grid(row=0, column=2, padx=(8, 0))

        def _update(v):
            val_lbl.configure(text=f"{float(v):.2f}{suffix}")

        slider = ctk.CTkSlider(frame, from_=from_, to=to, variable=var,
                               progress_color=ACCENT, button_color=ACCENT,
                               command=_update)
        slider.grid(row=0, column=1, sticky="ew", padx=(12, 8))
        return row + 1

    # ── Save ───────────────────────────────────────────────────────────────────

    def _save(self):
        try:
            port = int(self._port_var.get())
            if not (1 <= port <= 65535):
                raise ValueError
        except ValueError:
            self._save_status.configure(text="[!] Invalid port number", text_color=ERROR)
            return

        # Build skill_positions dict from StringVars
        skill_positions = {}
        for key, vars_ in self._pos_vars.items():
            try:
                x = int(vars_["x"].get())
                y = int(vars_["y"].get())
            except (ValueError, TypeError):
                x, y = -1, -1
            skill_positions[key] = {"x": x, "y": y}

        new_settings = {
            "port":               port,
            "start_on_launch":    self._sol_var.get(),
            "mouse_sensitivity":  round(self._ms_var.get(), 2),
            "joystick_threshold": round(self._jt_var.get(), 2),
            "skill_aim_distance": int(self._sad_var.get()),
            "invert_x":           self._settings.get("invert_x", False),
            "invert_y":           self._settings.get("invert_y", True),
            "skill_positions":    skill_positions,
        }
        try:
            with open(SETTINGS_FILE, "w") as f:
                json.dump(new_settings, f, indent=2)
            self._settings = new_settings
            self._on_save(new_settings)
            self._save_status.configure(text="[OK] Settings saved!", text_color=SUCCESS)
            self.after(3000, lambda: self._save_status.configure(text=""))
        except Exception as e:
            self._save_status.configure(text=f"[!] Error: {e}", text_color=ERROR)
