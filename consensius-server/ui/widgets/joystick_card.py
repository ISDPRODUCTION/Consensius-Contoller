from PySide6.QtWidgets import QFrame, QVBoxLayout, QHBoxLayout, QLabel
from PySide6.QtCore import Qt, QPointF
from PySide6.QtGui import QPainter, QColor, QPen

class JoystickVisualizer(QFrame):
    def __init__(self, title: str, parent=None):
        super().__init__(parent)
        self.title = title
        self.x_val = 0.0
        self.y_val = 0.0
        self.setFixedSize(140, 140)
        self.setStyleSheet("background-color: transparent;")

    def set_values(self, x: float, y: float):
        self.x_val = max(-1.0, min(1.0, x))
        self.y_val = max(-1.0, min(1.0, y))
        self.update()

    def paintEvent(self, event):
        painter = QPainter(self)
        painter.setRenderHint(QPainter.Antialiasing)

        width = self.width()
        height = self.height()
        center = QPointF(width / 2.0, height / 2.0)
        radius = min(width, height) / 2.0 - 10.0

        # Draw outer circle border
        painter.setPen(QPen(QColor("#45475a"), 2))
        painter.setBrush(QColor("#1e1e2e"))
        painter.drawEllipse(center, radius, radius)

        # Draw crosshairs
        painter.setPen(QPen(QColor("#313244"), 1, Qt.DashLine))
        painter.drawLine(int(center.x() - radius), int(center.y()), int(center.x() + radius), int(center.y()))
        painter.drawLine(int(center.x()), int(center.y() - radius), int(center.x()), int(center.y() + radius))

        # Draw joystick knob
        # Map values x, y from range [-1.0, 1.0] to visual circle radius
        # Note: In standard Cartesian coordinate system, Y goes UP (positive). In QPainter/Qt screen space, Y goes DOWN.
        # We invert the Y coordinate visually to match the standard Cartesian representation.
        knob_x = center.x() + (self.x_val * radius)
        knob_y = center.y() - (self.y_val * radius)
        knob_center = QPointF(knob_x, knob_y)

        painter.setPen(Qt.NoPen)
        painter.setBrush(QColor("#f38ba8" if self.title == "Left Stick" else "#89b4fa"))
        painter.drawEllipse(knob_center, 8.0, 8.0)

class JoystickCard(QFrame):
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setObjectName("JoystickCard")
        self.setStyleSheet("""
            #JoystickCard {
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
                margin-bottom: 5px;
            }
            .stick-title {
                font-weight: bold;
                font-size: 13px;
                color: #bac2de;
            }
            .coords {
                font-family: 'Consolas', 'Courier New', monospace;
                font-size: 12px;
                color: #a6adc8;
            }
        """)
        self._init_ui()

    def _init_ui(self):
        main_layout = QVBoxLayout(self)
        
        title = QLabel("Joysticks (Live)")
        title.setProperty("class", "title")
        main_layout.addWidget(title)
        
        sticks_layout = QHBoxLayout()
        
        # Left Stick Layout
        left_layout = QVBoxLayout()
        left_layout.setAlignment(Qt.AlignCenter)
        lbl_left = QLabel("Left Stick")
        lbl_left.setProperty("class", "stick-title")
        lbl_left.setAlignment(Qt.AlignCenter)
        self.left_viz = JoystickVisualizer("Left Stick")
        self.lbl_left_coords = QLabel("X: 0.00 | Y: 0.00")
        self.lbl_left_coords.setProperty("class", "coords")
        self.lbl_left_coords.setAlignment(Qt.AlignCenter)
        
        left_layout.addWidget(lbl_left)
        left_layout.addWidget(self.left_viz)
        left_layout.addWidget(self.lbl_left_coords)
        
        # Right Stick Layout
        right_layout = QVBoxLayout()
        right_layout.setAlignment(Qt.AlignCenter)
        lbl_right = QLabel("Right Stick")
        lbl_right.setProperty("class", "stick-title")
        lbl_right.setAlignment(Qt.AlignCenter)
        self.right_viz = JoystickVisualizer("Right Stick")
        self.lbl_right_coords = QLabel("X: 0.00 | Y: 0.00")
        self.lbl_right_coords.setProperty("class", "coords")
        self.lbl_right_coords.setAlignment(Qt.AlignCenter)
        
        right_layout.addWidget(lbl_right)
        right_layout.addWidget(self.right_viz)
        right_layout.addWidget(self.lbl_right_coords)
        
        sticks_layout.addLayout(left_layout)
        sticks_layout.addSpacing(20)
        sticks_layout.addLayout(right_layout)
        
        main_layout.addLayout(sticks_layout)

    def update_state(self, controller_state):
        if not controller_state:
            return
            
        # Swap left and right states to align physical controller input mapping with correct dashboard visualizer widgets
        left_x = controller_state.right.x
        left_y = controller_state.right.y
        right_x = controller_state.left.x
        right_y = controller_state.left.y

        self.left_viz.set_values(left_x, left_y)
        self.lbl_left_coords.setText(f"X: {left_x:+.2f} | Y: {left_y:+.2f}")

        self.right_viz.set_values(right_x, right_y)
        self.lbl_right_coords.setText(f"X: {right_x:+.2f} | Y: {right_y:+.2f}")
