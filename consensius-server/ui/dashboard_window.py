from PySide6.QtWidgets import (
    QMainWindow, QWidget, QVBoxLayout, QHBoxLayout, QGridLayout, 
    QLabel, QPushButton, QPlainTextEdit, QFrame, QSplitter,
    QStackedWidget, QTableWidget, QTableWidgetItem, QHeaderView,
    QDialog, QFormLayout, QLineEdit, QMessageBox, QListWidget, QListWidgetItem
)
from PySide6.QtCore import QTimer, Qt
from PySide6.QtGui import QFont
from ui.widgets.connection_card import ConnectionCard
from ui.widgets.joystick_card import JoystickCard
from ui.widgets.buttons_card import ButtonsCard
from ui.widgets.events_card import EventsCard

class CreateProfileDialog(QDialog):
    def __init__(self, profile_manager, parent=None):
        super().__init__(parent)
        self.profile_manager = profile_manager
        self.setWindowTitle("Create Profile")
        self.setMinimumWidth(350)
        self.setStyleSheet("""
            QDialog {
                background-color: #1e1e2e;
                color: #cdd6f4;
            }
            QLabel {
                color: #cdd6f4;
                font-family: 'Segoe UI', Arial, sans-serif;
                font-weight: bold;
            }
            QLineEdit {
                background-color: #11111b;
                border: 1px solid #313244;
                border-radius: 4px;
                color: #cdd6f4;
                padding: 6px;
            }
            QPushButton {
                background-color: #313244;
                color: #cdd6f4;
                border: 1px solid #45475a;
                border-radius: 4px;
                padding: 8px 15px;
            }
            QPushButton:hover {
                background-color: #45475a;
            }
            QPushButton#btn-ok {
                background-color: #89b4fa;
                color: #11111b;
                font-weight: bold;
            }
            QPushButton#btn-ok:hover {
                background-color: #b4befe;
            }
        """)
        
        self._init_ui()

    def _init_ui(self):
        layout = QVBoxLayout(self)
        form = QFormLayout()
        
        self.txt_name = QLineEdit()
        self.txt_name.setPlaceholderText("Mobile Legends")
        
        self.txt_id = QLineEdit()
        self.txt_id.setPlaceholderText("mlbb (lowercase, no spaces)")
        
        form.addRow("Profile Name:", self.txt_name)
        form.addRow("Profile ID:", self.txt_id)
        
        layout.addLayout(form)
        
        buttons = QHBoxLayout()
        self.btn_cancel = QPushButton("Cancel")
        self.btn_cancel.clicked.connect(self.reject)
        
        self.btn_ok = QPushButton("Create")
        self.btn_ok.setObjectName("btn-ok")
        self.btn_ok.clicked.connect(self._validate_and_accept)
        
        buttons.addStretch()
        buttons.addWidget(self.btn_cancel)
        buttons.addWidget(self.btn_ok)
        
        layout.addLayout(buttons)

    def _validate_and_accept(self):
        name = self.txt_name.text().strip()
        pid = self.txt_id.text().strip().lower()
        
        if not name:
            QMessageBox.warning(self, "Validation Error", "Profile name cannot be empty.")
            return
            
        if not pid:
            QMessageBox.warning(self, "Validation Error", "Profile ID cannot be empty.")
            return
            
        if " " in pid:
            QMessageBox.warning(self, "Validation Error", "Profile ID cannot contain spaces.")
            return
            
        if not pid.isalnum():
            QMessageBox.warning(self, "Validation Error", "Profile ID must be alphanumeric.")
            return
            
        if self.profile_manager.get_profile(pid):
            QMessageBox.warning(self, "Validation Error", f"Profile ID '{pid}' already exists.")
            return
            
        # Call creation
        self.created_profile = self.profile_manager.create_profile(pid, name)
        if self.created_profile:
            self.accept()
        else:
            QMessageBox.critical(self, "System Error", "Failed to create profile.")
            self.reject()

class DashboardWindow(QMainWindow):
    def __init__(self, state_manager, profile_manager, config_manager, server, event_logger, event_inspector, movement_simulator):
        super().__init__()
        self.state_manager = state_manager
        self.profile_manager = profile_manager
        self.config_manager = config_manager
        self.server = server
        self.event_logger = event_logger
        self.event_inspector = event_inspector
        self.movement_simulator = movement_simulator
        self.default_actions = {"skill1", "skill2", "skill3", "attack", "spell", "recall", "regen"}
        
        self.setWindowTitle("Consensius Host")
        self.resize(1200, 800)
        
        # Dark Theme stylesheet
        self.setStyleSheet("""
            QMainWindow {
                background-color: #11111b;
            }
            #Sidebar {
                background-color: #181825;
                border-right: 1px solid #313244;
                min-width: 220px;
                max-width: 220px;
            }
            #ContentArea {
                background-color: #11111b;
            }
            QLabel {
                font-family: 'Segoe UI', Arial, sans-serif;
            }
            .sidebar-title {
                color: #cdd6f4;
                font-size: 18px;
                font-weight: bold;
                padding: 20px 10px;
                border-bottom: 1px solid #313244;
                margin-bottom: 20px;
            }
            QPushButton.nav-btn {
                background-color: transparent;
                border: none;
                color: #a6adc8;
                font-family: 'Segoe UI', Arial, sans-serif;
                font-size: 14px;
                text-align: left;
                padding: 10px 15px;
                margin: 2px 10px;
                border-radius: 5px;
            }
            QPushButton.nav-btn:hover {
                background-color: #313244;
                color: #cdd6f4;
            }
            QPushButton.nav-btn:checked {
                background-color: #89b4fa;
                color: #11111b;
                font-weight: bold;
            }
            QPushButton.nav-disabled {
                background-color: transparent;
                border: none;
                color: #585b70;
                font-family: 'Segoe UI', Arial, sans-serif;
                font-size: 14px;
                text-align: left;
                padding: 10px 15px;
                margin: 2px 10px;
            }
            #LogPanel {
                background-color: #1e1e2e;
                border: 1px solid #313244;
                border-radius: 8px;
                padding: 15px;
            }
            .panel-title {
                font-size: 16px;
                font-weight: bold;
                color: #89b4fa;
                margin-bottom: 10px;
            }
            QPlainTextEdit#LogView {
                background-color: #11111b;
                border: 1px solid #313244;
                border-radius: 6px;
                color: #a6e3a1;
                font-family: 'Consolas', 'Courier New', monospace;
                font-size: 12px;
            }
            QTableWidget {
                background-color: #1e1e2e;
                border: 1px solid #313244;
                border-radius: 6px;
                gridline-color: #313244;
                color: #cdd6f4;
                font-family: 'Segoe UI', Arial, sans-serif;
            }
            QHeaderView::section {
                background-color: #181825;
                color: #89b4fa;
                padding: 6px;
                border: 1px solid #313244;
                font-weight: bold;
            }
            QPushButton.action-btn {
                background-color: #313244;
                color: #cdd6f4;
                border: 1px solid #45475a;
                border-radius: 4px;
                padding: 8px 15px;
                font-weight: bold;
            }
            QPushButton.action-btn:hover {
                background-color: #45475a;
            }
            QPushButton.action-highlight {
                background-color: #89b4fa;
                color: #11111b;
            }
            QPushButton.action-highlight:hover {
                background-color: #b4befe;
            }
        """)
        
        self._init_ui()
        
        # Setup Timer for 100ms refresh rate
        self.timer = QTimer(self)
        self.timer.timeout.connect(self._refresh_data)
        self.timer.start(100)

    def _init_ui(self):
        central_widget = QWidget()
        self.setCentralWidget(central_widget)
        
        main_layout = QHBoxLayout(central_widget)
        main_layout.setContentsMargins(0, 0, 0, 0)
        main_layout.setSpacing(0)
        
        # 1. Sidebar Panel
        sidebar = QFrame()
        sidebar.setObjectName("Sidebar")
        sidebar_layout = QVBoxLayout(sidebar)
        sidebar_layout.setContentsMargins(0, 0, 0, 0)
        sidebar_layout.setSpacing(5)
        
        sidebar_title = QLabel("CONSENSIUS")
        sidebar_title.setProperty("class", "sidebar-title")
        sidebar_title.setAlignment(Qt.AlignCenter)
        sidebar_layout.addWidget(sidebar_title)
        
        self.btn_dash = QPushButton("Dashboard")
        self.btn_dash.setProperty("class", "nav-btn")
        self.btn_dash.setCheckable(True)
        self.btn_dash.setChecked(True)
        self.btn_dash.clicked.connect(self._show_dashboard)
        sidebar_layout.addWidget(self.btn_dash)
        
        self.btn_profiles = QPushButton("Profiles")
        self.btn_profiles.setProperty("class", "nav-btn")
        self.btn_profiles.setCheckable(True)
        self.btn_profiles.clicked.connect(self._show_profiles)
        sidebar_layout.addWidget(self.btn_profiles)
        
        self.btn_keybinds = QPushButton("Keybinds")
        self.btn_keybinds.setProperty("class", "nav-btn")
        self.btn_keybinds.setCheckable(True)
        self.btn_keybinds.clicked.connect(self._show_keybinds)
        sidebar_layout.addWidget(self.btn_keybinds)
        
        # Placeholders/Disabled navigation buttons for future pages
        btn_settings = QPushButton("Settings (Soon)")
        btn_settings.setProperty("class", "nav-disabled")
        btn_settings.setEnabled(False)
        sidebar_layout.addWidget(btn_settings)
        
        btn_mapper = QPushButton("Input Mapper (Soon)")
        btn_mapper.setProperty("class", "nav-disabled")
        btn_mapper.setEnabled(False)
        sidebar_layout.addWidget(btn_mapper)
        
        btn_emulator = QPushButton("Emulator Adapter (Soon)")
        btn_emulator.setProperty("class", "nav-disabled")
        btn_emulator.setEnabled(False)
        sidebar_layout.addWidget(btn_emulator)
        
        sidebar_layout.addStretch()
        main_layout.addWidget(sidebar)
        
        # 2. Content Stack Area
        self.stack = QStackedWidget()
        
        # Page A: Dashboard Page
        dash_page = QWidget()
        dash_layout = QVBoxLayout(dash_page)
        dash_layout.setContentsMargins(20, 20, 20, 20)
        dash_layout.setSpacing(20)
        
        splitter = QSplitter(Qt.Vertical)
        cards_widget = QWidget()
        cards_layout = QGridLayout(cards_widget)
        cards_layout.setContentsMargins(0, 0, 0, 0)
        cards_layout.setSpacing(15)
        
        self.conn_card = ConnectionCard()
        self.joy_card = JoystickCard()
        self.btn_card = ButtonsCard()
        self.evt_card = EventsCard()
        
        cards_layout.addWidget(self.conn_card, 0, 0)
        cards_layout.addWidget(self.joy_card, 1, 0)
        cards_layout.addWidget(self.btn_card, 2, 0)
        cards_layout.addWidget(self.evt_card, 0, 1, 3, 1)
        cards_layout.setColumnStretch(0, 3)
        cards_layout.setColumnStretch(1, 2)
        splitter.addWidget(cards_widget)
        
        # Bottom split: Server Log Panel (Left), Movement State (Middle) & Event Inspector (Right)
        bottom_widget = QWidget()
        bottom_layout = QHBoxLayout(bottom_widget)
        bottom_layout.setContentsMargins(0, 0, 0, 0)
        bottom_layout.setSpacing(15)
        
        # Left Panel: Server Log Panel
        log_panel = QFrame()
        log_panel.setObjectName("LogPanel")
        log_layout = QVBoxLayout(log_panel)
        log_layout.setContentsMargins(15, 15, 15, 15)
        
        log_title = QLabel("Server Log Panel")
        log_title.setProperty("class", "panel-title")
        log_layout.addWidget(log_title)
        
        self.log_view = QPlainTextEdit()
        self.log_view.setObjectName("LogView")
        self.log_view.setReadOnly(True)
        log_layout.addWidget(self.log_view)
        bottom_layout.addWidget(log_panel, 2)
        
        # Middle Panel: Movement State
        move_panel = QFrame()
        move_panel.setObjectName("LogPanel")
        move_layout = QVBoxLayout(move_panel)
        move_layout.setContentsMargins(15, 15, 15, 15)
        
        move_title = QLabel("Movement State")
        move_title.setProperty("class", "panel-title")
        move_layout.addWidget(move_title)
        
        wasd_layout = QGridLayout()
        wasd_layout.setSpacing(10)
        
        self.lbl_move_w = QLabel("W : OFF")
        self.lbl_move_a = QLabel("A : OFF")
        self.lbl_move_s = QLabel("S : OFF")
        self.lbl_move_d = QLabel("D : OFF")
        
        for lbl in (self.lbl_move_w, self.lbl_move_a, self.lbl_move_s, self.lbl_move_d):
            lbl.setStyleSheet("color: #f38ba8; font-family: 'Segoe UI', Arial, sans-serif; font-size: 14px; font-weight: bold;")
            lbl.setAlignment(Qt.AlignCenter)
            
        wasd_layout.addWidget(self.lbl_move_w, 0, 1)
        wasd_layout.addWidget(self.lbl_move_a, 1, 0)
        wasd_layout.addWidget(self.lbl_move_s, 1, 1)
        wasd_layout.addWidget(self.lbl_move_d, 1, 2)
        
        move_layout.addLayout(wasd_layout)
        move_layout.addStretch()
        bottom_layout.addWidget(move_panel, 1)
        
        # Right Panel: Event Inspector
        inspector_panel = QFrame()
        inspector_panel.setObjectName("LogPanel") # reuse style
        inspector_layout = QVBoxLayout(inspector_panel)
        inspector_layout.setContentsMargins(15, 15, 15, 15)
        
        header_layout = QHBoxLayout()
        inspector_title = QLabel("Event Inspector")
        inspector_title.setProperty("class", "panel-title")
        
        self.lbl_latency = QLabel("Latency: 0ms")
        self.lbl_latency.setStyleSheet("color: #fab387; font-weight: bold; font-family: monospace; font-size: 13px;")
        
        self.btn_debug_toggle = QPushButton("Debug: ON")
        self.btn_debug_toggle.setProperty("class", "action-btn action-highlight")
        self.btn_debug_toggle.setFixedWidth(100)
        self.btn_debug_toggle.clicked.connect(self._toggle_debug_mode)
        
        header_layout.addWidget(inspector_title)
        header_layout.addStretch()
        header_layout.addWidget(self.lbl_latency)
        header_layout.addWidget(self.btn_debug_toggle)
        inspector_layout.addLayout(header_layout)
        
        self.list_inspector = QListWidget()
        self.list_inspector.setStyleSheet("""
            background-color: #11111b;
            border: 1px solid #313244;
            border-radius: 6px;
            color: #cdd6f4;
            font-family: 'Consolas', 'Courier New', monospace;
            font-size: 11px;
        """)
        inspector_layout.addWidget(self.list_inspector)
        bottom_layout.addWidget(inspector_panel, 2)
        
        splitter.addWidget(bottom_widget)
        
        splitter.setSizes([500, 300])
        dash_layout.addWidget(splitter)
        self.stack.addWidget(dash_page)
        
        # Page B: Profiles Page
        prof_page = QWidget()
        prof_layout = QVBoxLayout(prof_page)
        prof_layout.setContentsMargins(20, 20, 20, 20)
        prof_layout.setSpacing(20)
        
        # Title Header
        lbl_prof_title = QLabel("Profile Manager")
        lbl_prof_title.setProperty("class", "panel-title")
        lbl_prof_title.setStyleSheet("font-size: 22px; color: #89b4fa;")
        prof_layout.addWidget(lbl_prof_title)
        
        # Action Buttons bar
        buttons_layout = QHBoxLayout()
        self.btn_create = QPushButton("Create Profile")
        self.btn_create.setProperty("class", "action-btn action-highlight")
        self.btn_create.clicked.connect(self._create_profile)
        
        self.btn_delete = QPushButton("Delete Profile")
        self.btn_delete.setProperty("class", "action-btn")
        self.btn_delete.clicked.connect(self._delete_profile)
        
        self.btn_set_active = QPushButton("Set Active")
        self.btn_set_active.setProperty("class", "action-btn action-highlight")
        self.btn_set_active.clicked.connect(self._set_active_profile)
        
        self.btn_refresh = QPushButton("Refresh")
        self.btn_refresh.setProperty("class", "action-btn")
        self.btn_refresh.clicked.connect(self._refresh_profiles_table)
        
        buttons_layout.addWidget(self.btn_create)
        buttons_layout.addWidget(self.btn_delete)
        buttons_layout.addWidget(self.btn_set_active)
        buttons_layout.addStretch()
        buttons_layout.addWidget(self.btn_refresh)
        prof_layout.addLayout(buttons_layout)
        
        # Table of Available Profiles
        self.tbl_profiles = QTableWidget()
        self.tbl_profiles.setColumnCount(4)
        self.tbl_profiles.setHorizontalHeaderLabels(["Name", "ID", "Bindings Count", "Created Date"])
        self.tbl_profiles.horizontalHeader().setSectionResizeMode(QHeaderView.Stretch)
        self.tbl_profiles.setSelectionBehavior(QTableWidget.SelectRows)
        self.tbl_profiles.setSelectionMode(QTableWidget.SingleSelection)
        self.tbl_profiles.setEditTriggers(QTableWidget.NoEditTriggers)
        prof_layout.addWidget(self.tbl_profiles)
        
        self.stack.addWidget(prof_page)
        
        # Page C: Keybind Editor Page (Layer 1.8)
        kb_page = QWidget()
        kb_layout = QVBoxLayout(kb_page)
        kb_layout.setContentsMargins(20, 20, 20, 20)
        kb_layout.setSpacing(20)
        
        self.lbl_kb_title = QLabel("Keybind Editor - Active Profile: None")
        self.lbl_kb_title.setProperty("class", "panel-title")
        self.lbl_kb_title.setStyleSheet("font-size: 22px; color: #89b4fa;")
        kb_layout.addWidget(self.lbl_kb_title)
        
        kb_buttons = QHBoxLayout()
        self.btn_add_action = QPushButton("Add Action")
        self.btn_add_action.setProperty("class", "action-btn action-highlight")
        self.btn_add_action.clicked.connect(self._add_keybind_row)
        
        self.btn_del_action = QPushButton("Delete Action")
        self.btn_del_action.setProperty("class", "action-btn")
        self.btn_del_action.clicked.connect(self._delete_keybind_row)
        
        self.btn_save_kb = QPushButton("Save Changes")
        self.btn_save_kb.setProperty("class", "action-btn action-highlight")
        self.btn_save_kb.clicked.connect(self._save_keybinds)
        
        self.btn_reload_kb = QPushButton("Reload")
        self.btn_reload_kb.setProperty("class", "action-btn")
        self.btn_reload_kb.clicked.connect(self._load_keybinds_table)
        
        kb_buttons.addWidget(self.btn_add_action)
        kb_buttons.addWidget(self.btn_del_action)
        kb_buttons.addWidget(self.btn_save_kb)
        kb_buttons.addStretch()
        kb_buttons.addWidget(self.btn_reload_kb)
        kb_layout.addLayout(kb_buttons)
        
        # Keybinds Table
        self.tbl_keybinds = QTableWidget()
        self.tbl_keybinds.setColumnCount(2)
        self.tbl_keybinds.setHorizontalHeaderLabels(["Action", "Key"])
        self.tbl_keybinds.horizontalHeader().setSectionResizeMode(QHeaderView.Stretch)
        self.tbl_keybinds.setSelectionBehavior(QTableWidget.SelectRows)
        self.tbl_keybinds.setSelectionMode(QTableWidget.SingleSelection)
        kb_layout.addWidget(self.tbl_keybinds)
        
        self.stack.addWidget(kb_page)
        
        main_layout.addWidget(self.stack)
        
        # Build initial profile list
        self._refresh_profiles_table()

    def _show_dashboard(self):
        self.btn_dash.setChecked(True)
        self.btn_profiles.setChecked(False)
        self.btn_keybinds.setChecked(False)
        self.stack.setCurrentIndex(0)

    def _show_profiles(self):
        self.btn_dash.setChecked(False)
        self.btn_profiles.setChecked(True)
        self.btn_keybinds.setChecked(False)
        self.stack.setCurrentIndex(1)
        self._refresh_profiles_table()

    def _show_keybinds(self):
        self.btn_dash.setChecked(False)
        self.btn_profiles.setChecked(False)
        self.btn_keybinds.setChecked(True)
        self.stack.setCurrentIndex(2)
        self._load_keybinds_table()

    def _refresh_profiles_table(self):
        self.profile_manager.load_profiles()
        profiles_list = self.profile_manager.get_profiles()
        
        self.tbl_profiles.setRowCount(len(profiles_list))
        for r_idx, profile in enumerate(profiles_list):
            is_active = (profile.id == self.profile_manager.active_profile_key)
            name_text = f"{profile.name} (Active)" if is_active else profile.name
            
            item_name = QTableWidgetItem(name_text)
            item_id = QTableWidgetItem(profile.id)
            item_bindings = QTableWidgetItem(str(len(profile.bindings)))
            item_created = QTableWidgetItem(profile.metadata.created_at or "Unknown")
            
            if is_active:
                for item in (item_name, item_id, item_bindings, item_created):
                    font = QFont()
                    font.setBold(True)
                    item.setFont(font)
                    item.setForeground(Qt.GlobalColor.green)
            
            self.tbl_profiles.setItem(r_idx, 0, item_name)
            self.tbl_profiles.setItem(r_idx, 1, item_id)
            self.tbl_profiles.setItem(r_idx, 2, item_bindings)
            self.tbl_profiles.setItem(r_idx, 3, item_created)

    def _create_profile(self):
        dialog = CreateProfileDialog(self.profile_manager, self)
        if dialog.exec() == QDialog.Accepted:
            self.event_logger.log_info(f"Created new profile: {dialog.created_profile.name}")
            self._refresh_profiles_table()

    def _delete_profile(self):
        selected_ranges = self.tbl_profiles.selectedRanges()
        if not selected_ranges:
            QMessageBox.warning(self, "Selection Error", "Please select a profile to delete.")
            return
            
        row = selected_ranges[0].topRow()
        profile_id = self.tbl_profiles.item(row, 1).text()
        
        if profile_id == self.profile_manager.active_profile_key:
            QMessageBox.warning(self, "Action Error", "Cannot delete the active profile.")
            return
            
        reply = QMessageBox.question(
            self, "Confirm Delete", 
            f"Are you sure you want to permanently delete profile '{profile_id}'?",
            QMessageBox.Yes | QMessageBox.No
        )
        
        if reply == QMessageBox.Yes:
            if self.profile_manager.delete_profile(profile_id):
                self.event_logger.log_info(f"Deleted profile: {profile_id}")
                self._refresh_profiles_table()
            else:
                QMessageBox.critical(self, "Error", f"Failed to delete profile '{profile_id}'.")

    def _set_active_profile(self):
        selected_ranges = self.tbl_profiles.selectedRanges()
        if not selected_ranges:
            QMessageBox.warning(self, "Selection Error", "Please select a profile to set active.")
            return
            
        row = selected_ranges[0].topRow()
        profile_id = self.tbl_profiles.item(row, 1).text()
        
        if self.profile_manager.set_active_profile(profile_id):
            self.config_manager.update_config({"active_profile": profile_id})
            self.event_logger.log_info(f"Set active profile changed to: {profile_id}")
            self._refresh_profiles_table()
            self.conn_card.update_status(self.server, self.profile_manager)
        else:
            QMessageBox.critical(self, "Error", f"Failed to set active profile '{profile_id}'.")

    def _load_keybinds_table(self):
        # Clears and populates the table based on active profile
        self.tbl_keybinds.setRowCount(0)
        active_profile = self.profile_manager.get_active_profile()
        
        if not active_profile:
            self.lbl_kb_title.setText("Keybind Editor - Active Profile: None")
            self.btn_add_action.setEnabled(False)
            self.btn_del_action.setEnabled(False)
            self.btn_save_kb.setEnabled(False)
            return
            
        self.lbl_kb_title.setText(f"Keybind Editor - Active Profile: {active_profile.name}")
        self.btn_add_action.setEnabled(True)
        self.btn_del_action.setEnabled(True)
        self.btn_save_kb.setEnabled(True)
        
        self.tbl_keybinds.setRowCount(len(active_profile.bindings))
        
        for r_idx, (action, key) in enumerate(active_profile.bindings.items()):
            item_action = QTableWidgetItem(action)
            item_key = QTableWidgetItem(key)
            
            # Default actions cannot be renamed (column 0 read-only)
            if action in self.default_actions:
                item_action.setFlags(Qt.ItemIsEnabled | Qt.ItemIsSelectable)
                item_action.setForeground(Qt.GlobalColor.gray)
            else:
                item_action.setFlags(Qt.ItemIsEnabled | Qt.ItemIsSelectable | Qt.ItemIsEditable)
                
            item_key.setFlags(Qt.ItemIsEnabled | Qt.ItemIsSelectable | Qt.ItemIsEditable)
            
            self.tbl_keybinds.setItem(r_idx, 0, item_action)
            self.tbl_keybinds.setItem(r_idx, 1, item_key)

    def _add_keybind_row(self):
        row_count = self.tbl_keybinds.rowCount()
        self.tbl_keybinds.insertRow(row_count)
        
        # Determine unique default custom action name
        existing_actions = set()
        for r in range(row_count):
            item = self.tbl_keybinds.item(r, 0)
            if item:
                existing_actions.add(item.text().strip().lower())
                
        custom_idx = 1
        while f"custom_action_{custom_idx}" in existing_actions:
            custom_idx += 1
            
        default_name = f"custom_action_{custom_idx}"
        
        item_action = QTableWidgetItem(default_name)
        item_action.setFlags(Qt.ItemIsEnabled | Qt.ItemIsSelectable | Qt.ItemIsEditable)
        
        item_key = QTableWidgetItem("none")
        item_key.setFlags(Qt.ItemIsEnabled | Qt.ItemIsSelectable | Qt.ItemIsEditable)
        
        self.tbl_keybinds.setItem(row_count, 0, item_action)
        self.tbl_keybinds.setItem(row_count, 1, item_key)
        self.tbl_keybinds.selectRow(row_count)

    def _delete_keybind_row(self):
        selected_ranges = self.tbl_keybinds.selectedRanges()
        if not selected_ranges:
            QMessageBox.warning(self, "Selection Error", "Please select a keybind row to delete.")
            return
            
        row = selected_ranges[0].topRow()
        item_action = self.tbl_keybinds.item(row, 0)
        if not item_action:
            return
            
        action_name = item_action.text().strip().lower()
        if action_name in self.default_actions:
            QMessageBox.warning(self, "Action Denied", f"Default action '{action_name}' cannot be deleted.")
            return
            
        reply = QMessageBox.question(
            self, "Confirm Delete",
            f"Are you sure you want to delete action '{action_name}'?",
            QMessageBox.Yes | QMessageBox.No
        )
        
        if reply == QMessageBox.Yes:
            self.tbl_keybinds.removeRow(row)

    def _save_keybinds(self):
        active_profile = self.profile_manager.get_active_profile()
        if not active_profile:
            return
            
        bindings = {}
        for r in range(self.tbl_keybinds.rowCount()):
            item_action = self.tbl_keybinds.item(r, 0)
            item_key = self.tbl_keybinds.item(r, 1)
            
            if not item_action or not item_key:
                continue
                
            action = item_action.text().strip().lower()
            key = item_key.text().strip().lower()
            
            if not action:
                QMessageBox.critical(self, "Validation Error", f"Action name at row {r+1} cannot be empty.")
                return
                
            if not key:
                QMessageBox.critical(self, "Validation Error", f"Key value for action '{action}' cannot be empty.")
                return
                
            if " " in action:
                QMessageBox.critical(self, "Validation Error", f"Action name '{action}' cannot contain spaces.")
                return
                
            if action in bindings:
                QMessageBox.critical(self, "Validation Error", f"Duplicate action name '{action}' detected.")
                return
                
            bindings[action] = key
            
        # Save back to active profile
        active_profile.bindings = bindings
        if self.profile_manager.save_profile(active_profile):
            self.event_logger.log_info(f"Saved keybinds for profile: {active_profile.name}")
            QMessageBox.information(self, "Success", "Keybinds saved successfully.")
            self._load_keybinds_table()
            self.conn_card.update_status(self.server, self.profile_manager)
        else:
            QMessageBox.critical(self, "Error", "Failed to save keybinds to profile file.")

    def _refresh_data(self):
        controller_state = self.state_manager.get_state()
        
        self.conn_card.update_status(self.server, self.profile_manager)
        self.joy_card.update_state(controller_state)
        self.btn_card.update_state(controller_state)
        
        recent_rx = self.event_logger.get_recent_rx_events()
        self.evt_card.update_events(recent_rx)
        
        recent_logs = self.event_logger.get_recent_logs()
        current_text = self.log_view.toPlainText()
        new_text = "\n".join(recent_logs)
        
        if current_text != new_text:
            scrollbar = self.log_view.verticalScrollBar()
            was_at_bottom = scrollbar.value() == scrollbar.maximum()
            
            self.log_view.setPlainText(new_text)
            
            if was_at_bottom:
                scrollbar.setValue(scrollbar.maximum())

        # Update Event Inspector items & latency
        latency_val = self.event_inspector.get_latency()
        self.lbl_latency.setText(f"Latency: {int(latency_val)}ms")
        
        events = self.event_inspector.get_events()
        self.list_inspector.clear()
        self.list_inspector.addItems(events)

        # Update Movement Simulator states
        move_states = self.movement_simulator.get_states()
        for key, active in move_states.items():
            lbl = getattr(self, f"lbl_move_{key.lower()}")
            status_text = "ON" if active else "OFF"
            lbl.setText(f"{key} : {status_text}")
            if active:
                lbl.setStyleSheet("color: #a6e3a1; font-family: 'Segoe UI', Arial, sans-serif; font-size: 14px; font-weight: bold;")
            else:
                lbl.setStyleSheet("color: #f38ba8; font-family: 'Segoe UI', Arial, sans-serif; font-size: 14px; font-weight: bold;")

    def _toggle_debug_mode(self):
        enabled = not self.event_inspector.debug_mode
        self.event_inspector.set_debug_mode(enabled)
        if enabled:
            self.btn_debug_toggle.setText("Debug: ON")
            self.btn_debug_toggle.setStyleSheet("background-color: #89b4fa; color: #11111b;")
        else:
            self.btn_debug_toggle.setText("Debug: OFF")
            self.btn_debug_toggle.setStyleSheet("background-color: #313244; color: #cdd6f4;")
