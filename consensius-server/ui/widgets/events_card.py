from PySide6.QtWidgets import QFrame, QVBoxLayout, QLabel, QListWidget, QListWidgetItem
from PySide6.QtCore import Qt

class EventsCard(QFrame):
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setObjectName("EventsCard")
        self.setStyleSheet("""
            #EventsCard {
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
            QListWidget {
                background-color: #11111b;
                border: 1px solid #313244;
                border-radius: 6px;
                color: #cdd6f4;
                font-family: 'Consolas', 'Courier New', monospace;
                font-size: 11px;
                padding: 5px;
            }
            QListWidget::item {
                border-bottom: 1px solid #1e1e2e;
                padding: 4px;
            }
            QListWidget::item:selected {
                background-color: #313244;
                color: #f5e0dc;
            }
        """)
        self._init_ui()

    def _init_ui(self):
        layout = QVBoxLayout(self)
        
        title = QLabel("Recent Input Events")
        title.setProperty("class", "title")
        layout.addWidget(title)
        
        self.list_widget = QListWidget()
        layout.addWidget(self.list_widget)

    def update_events(self, recent_rx_events):
        self.list_widget.clear()
        
        # Limit to latest 100 events
        events_to_show = recent_rx_events[:100]
        for event in events_to_show:
            item = QListWidgetItem(event)
            # Custom coloration for joystick vs buttons
            if "joystick" in event:
                item.setForeground(Qt.GlobalColor.cyan)
            elif "button" in event:
                if "DOWN" in event:
                    item.setForeground(Qt.GlobalColor.green)
                else:
                    item.setForeground(Qt.GlobalColor.yellow)
            self.list_widget.addItem(item)
