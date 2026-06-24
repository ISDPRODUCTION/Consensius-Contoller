from PySide6.QtWidgets import QFrame, QVBoxLayout, QGridLayout, QLabel
from PySide6.QtCore import Qt

class ButtonIndicator(QFrame):
    def __init__(self, name: str, parent=None):
        super().__init__(parent)
        self.name = name
        self.setFrameShape(QFrame.StyledPanel)
        self.set_active(False)

    def set_active(self, active: bool):
        if active:
            # Active styling (glowing green/teal)
            self.setStyleSheet(f"""
                border: 1px solid #a6e3a1;
                background-color: #2e3d30;
                border-radius: 6px;
                padding: 10px;
            """)
            label_style = "color: #a6e3a1; font-weight: bold; font-size: 13px;"
            status_style = "color: #a6e3a1; font-weight: bold; font-size: 11px;"
            status_text = "ON"
        else:
            # Inactive styling (dimmed)
            self.setStyleSheet("""
                border: 1px solid #313244;
                background-color: #11111b;
                border-radius: 6px;
                padding: 10px;
            """)
            label_style = "color: #7f849c; font-weight: normal; font-size: 13px;"
            status_style = "color: #585b70; font-size: 11px;"
            status_text = "OFF"

        # Clear existing layout and reconstruct
        if self.layout():
            # Repurpose existing widgets
            self.lbl_title.setStyleSheet(label_style)
            self.lbl_status.setStyleSheet(status_style)
            self.lbl_status.setText(status_text)
        else:
            layout = QVBoxLayout(self)
            layout.setContentsMargins(5, 5, 5, 5)
            layout.setSpacing(2)
            self.lbl_title = QLabel(self.name.capitalize())
            self.lbl_title.setStyleSheet(label_style)
            self.lbl_title.setAlignment(Qt.AlignCenter)
            self.lbl_status = QLabel(status_text)
            self.lbl_status.setStyleSheet(status_style)
            self.lbl_status.setAlignment(Qt.AlignCenter)
            layout.addWidget(self.lbl_title)
            layout.addWidget(self.lbl_status)

class ButtonsCard(QFrame):
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setObjectName("ButtonsCard")
        self.setStyleSheet("""
            #ButtonsCard {
                background-color: #1e1e2e;
                border: 1px solid #313244;
                border-radius: 8px;
                padding: 15px;
            }
            QLabel {
                color: #cdd6f4;
                font-family: 'Segoe UI', Arial, sans-serif;
            }
            .title {
                font-size: 16px;
                font-weight: bold;
                color: #89b4fa;
                margin-bottom: 10px;
            }
        """)
        self.buttons_to_display = ["attack", "skill1", "skill2", "skill3", "spell", "recall", "regen"]
        self.indicators = {}
        self._init_ui()

    def _init_ui(self):
        layout = QVBoxLayout(self)
        
        title = QLabel("Button States")
        title.setProperty("class", "title")
        layout.addWidget(title)
        
        grid = QGridLayout()
        grid.setSpacing(10)
        
        cols = 3
        for idx, btn_name in enumerate(self.buttons_to_display):
            indicator = ButtonIndicator(btn_name)
            self.indicators[btn_name] = indicator
            r = idx // cols
            c = idx % cols
            grid.addWidget(indicator, r, c)
            
        layout.addLayout(grid)

    def update_state(self, controller_state):
        if not controller_state:
            return
            
        # Get active buttons dictionary (e.g. {"skill1": "down"})
        btn_states = controller_state.buttons
        
        for btn_name, indicator in self.indicators.items():
            state = btn_states.get(btn_name)
            is_active = (state == "down")
            indicator.set_active(is_active)
