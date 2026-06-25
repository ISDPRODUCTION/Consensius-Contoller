"""
ui/about_page.py
================
About page: app info, version, description.
"""
import customtkinter as ctk
import tkinter as tk

BG      = "#0A0A0F"
CARD    = "#0F1624"
ACCENT  = "#0084FF"
TEXT    = "#E0E0E0"
TEXT2   = "#8899AA"
BORDER  = "#1A2535"
SUCCESS = "#00E676"


class AboutPage(ctk.CTkFrame):
    def __init__(self, master, **kwargs):
        super().__init__(master, fg_color=BG, **kwargs)
        self._build()

    def _build(self):
        self.grid_columnconfigure(0, weight=1)
        self.grid_rowconfigure(0, weight=1)

        card = ctk.CTkFrame(self, fg_color=CARD, corner_radius=16,
                            border_width=1, border_color=BORDER)
        card.grid(row=0, column=0, padx=60, pady=40, sticky="nsew")
        card.grid_columnconfigure(0, weight=1)

        # Logo circle
        canvas = tk.Canvas(card, width=80, height=80,
                           bg=CARD, highlightthickness=0)
        canvas.grid(row=0, column=0, pady=(40, 0))
        canvas.create_oval(4, 4, 76, 76, fill=ACCENT, outline="")
        canvas.create_text(40, 40, text="CC", fill="white",
                           font=("Consolas", 22, "bold"))

        ctk.CTkLabel(card, text="Consensius Server",
                     font=("Consolas", 22, "bold"), text_color=TEXT).grid(
                         row=1, column=0, pady=(12, 2))

        ctk.CTkLabel(card, text="v1.0.0",
                     font=("Consolas", 12), text_color=ACCENT).grid(
                         row=2, column=0, pady=(0, 20))

        sep = ctk.CTkFrame(card, height=1, fg_color=BORDER)
        sep.grid(row=3, column=0, sticky="ew", padx=40, pady=4)

        desc = (
            "A WebSocket-based PC server that receives controller\n"
            "inputs from the Consensius Controller Android app\n"
            "and translates them into real keyboard & mouse events\n"
            "for use with LDPlayer or any PC game."
        )
        ctk.CTkLabel(card, text=desc, font=("Consolas", 11),
                     text_color=TEXT2, justify="center").grid(
                         row=4, column=0, padx=40, pady=(16, 20))

        sep2 = ctk.CTkFrame(card, height=1, fg_color=BORDER)
        sep2.grid(row=5, column=0, sticky="ew", padx=40, pady=4)

        # Info rows
        info = ctk.CTkFrame(card, fg_color="transparent")
        info.grid(row=6, column=0, padx=60, pady=(16, 40))
        info.grid_columnconfigure((0, 1), weight=1)

        rows = [
            ("Protocol",   "WebSocket (ws://)"),
            ("Framework",  "CustomTkinter"),
            ("Input",      "pynput"),
            ("Python",     "3.10+"),
        ]
        for i, (label, value) in enumerate(rows):
            ctk.CTkLabel(info, text=label, font=("Consolas", 11),
                         text_color=TEXT2, anchor="w").grid(
                             row=i, column=0, sticky="w", pady=3)
            ctk.CTkLabel(info, text=value, font=("Consolas", 11, "bold"),
                         text_color=TEXT, anchor="e").grid(
                             row=i, column=1, sticky="e", pady=3, padx=(20, 0))

