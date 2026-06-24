import time
from PySide6.QtWidgets import QFrame, QVBoxLayout, QHBoxLayout, QLabel, QGridLayout
from PySide6.QtCore import Qt

class ConnectionCard(QFrame):
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setObjectName("ConnectionCard")
        self.setStyleSheet("""
            #ConnectionCard {
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
            .label {
                font-weight: bold;
                color: #a6adc8;
            }
            .value {
                color: #f5e0dc;
            }
            .status-running {
                color: #a6e3a1;
                font-weight: bold;
            }
            .status-stopped {
                color: #f38ba8;
                font-weight: bold;
            }
        """)
        self._init_ui()

    def _init_ui(self):
        layout = QVBoxLayout(self)
        
        title = QLabel("System Status & Profile")
        title.setProperty("class", "title")
        layout.addWidget(title)
        
        grid = QGridLayout()
        grid.setSpacing(10)
        
        # Row 0: Host Status & Port
        lbl_host_status = QLabel("Host Status:")
        lbl_host_status.setProperty("class", "label")
        self.val_host_status = QLabel("● Stopped")
        self.val_host_status.setProperty("class", "status-stopped")
        
        lbl_port = QLabel("Port:")
        lbl_port.setProperty("class", "label")
        self.val_port = QLabel("8080")
        self.val_port.setProperty("class", "value")
        
        grid.addWidget(lbl_host_status, 0, 0)
        grid.addWidget(self.val_host_status, 0, 1)
        grid.addWidget(lbl_port, 0, 2)
        grid.addWidget(self.val_port, 0, 3)
        
        # Row 1: Connected Clients & Uptime
        lbl_clients = QLabel("Clients Connected:")
        lbl_clients.setProperty("class", "label")
        self.val_clients = QLabel("0")
        self.val_clients.setProperty("class", "value")
        
        lbl_uptime = QLabel("Uptime:")
        lbl_uptime.setProperty("class", "label")
        self.val_uptime = QLabel("00:00:00")
        self.val_uptime.setProperty("class", "value")
        
        grid.addWidget(lbl_clients, 1, 0)
        grid.addWidget(self.val_clients, 1, 1)
        grid.addWidget(lbl_uptime, 1, 2)
        grid.addWidget(self.val_uptime, 1, 3)
        
        # Row 2: Connection Status & Last Client
        lbl_conn = QLabel("Connection:")
        lbl_conn.setProperty("class", "label")
        self.val_conn = QLabel("Disconnected")
        self.val_conn.setStyleSheet("color: #f38ba8; font-weight: bold;")
        
        lbl_last_client = QLabel("Last Client:")
        lbl_last_client.setProperty("class", "label")
        self.val_last_client = QLabel("None")
        self.val_last_client.setProperty("class", "value")
        
        grid.addWidget(lbl_conn, 2, 0)
        grid.addWidget(self.val_conn, 2, 1)
        grid.addWidget(lbl_last_client, 2, 2)
        grid.addWidget(self.val_last_client, 2, 3)
        
        # Row 3: Active Profile & Bindings Count
        lbl_profile = QLabel("Active Profile:")
        lbl_profile.setProperty("class", "label")
        self.val_profile = QLabel("None")
        self.val_profile.setStyleSheet("color: #fab387; font-weight: bold;")
        
        lbl_bindings = QLabel("Bindings:")
        lbl_bindings.setProperty("class", "label")
        self.val_bindings = QLabel("0 keys")
        self.val_bindings.setProperty("class", "value")
        
        grid.addWidget(lbl_profile, 3, 0)
        grid.addWidget(self.val_profile, 3, 1)
        grid.addWidget(lbl_bindings, 3, 2)
        grid.addWidget(self.val_bindings, 3, 3)
        
        # Row 4: Controller Settings (Deadzone, Sensitivity, Send Rate)
        lbl_ctrl = QLabel("Controller:")
        lbl_ctrl.setProperty("class", "label")
        self.val_ctrl = QLabel("DZ: 0.00 | Sens: 0.00 | Rate: 0Hz")
        self.val_ctrl.setProperty("class", "value")
        grid.addWidget(lbl_ctrl, 4, 0)
        grid.addWidget(self.val_ctrl, 4, 1, 1, 3)
        
        layout.addLayout(grid)

    def update_status(self, server, profile_manager):
        # Update server states
        if server and server.is_running:
            self.val_host_status.setText("● Running")
            self.val_host_status.setStyleSheet("color: #a6e3a1; font-weight: bold;")
            self.val_port.setText(str(server.port))
            
            connected_cnt = server.connected_count
            self.val_clients.setText(str(connected_cnt))
            
            if connected_cnt > 0:
                self.val_conn.setText("Connected")
                self.val_conn.setStyleSheet("color: #a6e3a1; font-weight: bold;")
            else:
                self.val_conn.setText("Disconnected")
                self.val_conn.setStyleSheet("color: #f38ba8; font-weight: bold;")
                
            # Uptime
            if server.start_time:
                uptime_sec = int(time.time() - server.start_time)
                h = uptime_sec // 3600
                m = (uptime_sec % 3600) // 60
                s = uptime_sec % 60
                self.val_uptime.setText(f"{h:02d}:{m:02d}:{s:02d}")
            else:
                self.val_uptime.setText("00:00:00")
                
            # Last client
            if server.last_client_address:
                self.val_last_client.setText(f"{server.last_client_address} @ {server.last_client_time}")
            else:
                self.val_last_client.setText("None")
        else:
            self.val_host_status.setText("● Stopped")
            self.val_host_status.setStyleSheet("color: #f38ba8; font-weight: bold;")
            self.val_clients.setText("0")
            self.val_conn.setText("Disconnected")
            self.val_conn.setStyleSheet("color: #f38ba8; font-weight: bold;")
            self.val_uptime.setText("00:00:00")
            self.val_last_client.setText("None")

        # Update profile state
        active_prof = profile_manager.get_active_profile() if profile_manager else None
        if active_prof:
            self.val_profile.setText(active_prof.name)
            self.val_bindings.setText(f"{len(active_prof.bindings)} keys")
            
            dz = active_prof.controller.deadzone
            sens = active_prof.controller.sensitivity
            rate = active_prof.controller.send_rate
            self.val_ctrl.setText(f"DZ: {dz:.2f} | Sens: {sens:.1f} | Rate: {rate}Hz")
        else:
            self.val_profile.setText("None")
            self.val_bindings.setText("0 keys")
            self.val_ctrl.setText("DZ: 0.00 | Sens: 0.00 | Rate: 0Hz")
