<div align="center">

# 🎮 Consensius Controller

**Ubah smartphone Android-mu menjadi gamepad nirkabel untuk PC**

[![Python](https://img.shields.io/badge/Python-3.11+-3776AB?style=for-the-badge&logo=python&logoColor=white)](https://python.org)
[![Android](https://img.shields.io/badge/Android-API%2026+-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![WebSocket](https://img.shields.io/badge/WebSocket-RFC%206455-FF6B35?style=for-the-badge)](https://tools.ietf.org/html/rfc6455)
[![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)](LICENSE)

</div>

---

## 📖 Deskripsi

**Consensius Controller** adalah sistem controller nirkabel dua komponen yang memungkinkan pengguna menggunakan smartphone Android mereka sebagai gamepad untuk bermain game di PC. Proyek ini terdiri dari:

- 🖥️ **Consensius Server** — Aplikasi desktop berbasis Python yang berjalan di PC, menerima input dari Android melalui WebSocket dan mensimulasikan aksi keyboard & mouse secara real-time.
- 📱 **Consensius Controller (Android)** — Aplikasi Android berbasis Kotlin + Jetpack Compose yang menampilkan antarmuka controller virtual dan mengirimkan input ke server.

Keduanya berkomunikasi melalui jaringan Wi-Fi lokal menggunakan protokol **WebSocket**, memastikan latensi rendah dan koneksi yang stabil tanpa bergantung pada internet.

---

## ✨ Fitur Utama

### 🖥️ Server (PC)

| Fitur | Deskripsi |
|---|---|
| 🎮 Input Joystick | Translasi joystick virtual ke gerakan karakter (WASD) |
| 🖱️ Mouse Emulation | Simulasi gerakan, klik kiri/kanan, dan scroll mouse |
| ⌨️ Button Mapping | Peta tombol controller ke keyboard shortcut yang bisa dikonfigurasi |
| 🕹️ D-Pad Support | Dukungan directional pad untuk navigasi dan ability |
| 🎯 Skill Aim System | Joystick aiming untuk skill berbasis arah (seperti skill MOBA) |
| 📋 Profile System | Manajemen profil per-game (MLBB, PUBG, Free Fire, Custom) |
| 📊 Live Monitor | Dashboard real-time untuk memantau semua input yang masuk |
| ⚙️ Settings GUI | Konfigurasi sensitivitas, deadzone, port, dan posisi skill aim |
| 📡 QR Code Connect | Generate QR Code agar Android bisa connect dengan mudah |
| 📝 Logging System | Log real-time dengan level INFO, RX, ENGINE, dan lainnya |
| 🔧 Auto-start | Opsi server langsung berjalan saat aplikasi dibuka |

### 📱 Android App

| Fitur | Deskripsi |
|---|---|
| 🕹️ Virtual Joystick | Joystick kiri (gerak) dan kanan (kamera/aim) dengan deadzone |
| 🔘 Custom Buttons | Tombol skill, attack, spell, recall yang bisa dikonfigurasi |
| 📷 QR Code Scanner | Scan QR Code dari server untuk koneksi instan |
| 📡 WebSocket Client | Koneksi real-time dengan reconnect otomatis |
| 🎨 Immersive UI | Tampilan fullscreen dengan tema gelap menggunakan Jetpack Compose |
| 💾 DataStore Prefs | Penyimpanan preferensi koneksi dan pengaturan secara persisten |
| 📂 Profile Sync | Sinkronisasi profil aktif dari server ke aplikasi Android |
| 🔧 Settings Screen | Konfigurasi sensitivitas dan preferensi lainnya |

---

## 🏗️ Arsitektur Sistem

```
┌──────────────────────────────────────────────────────────────────────┐
│                         Wi-Fi Local Network                          │
│                                                                      │
│  ┌─────────────────────┐   WebSocket (ws://)   ┌──────────────────┐ │
│  │   Android App       │ ◄───────────────────► │   PC Server      │ │
│  │  (Controller)       │   JSON Messages        │   (Python)       │ │
│  │                     │   port: 8765           │                  │ │
│  │  Jetpack Compose UI │                        │  CustomTkinter   │ │
│  │  WebSocketManager   │                        │  WebSocketServer │ │
│  │  OkHttp WebSocket   │                        │  InputHandler    │ │
│  └─────────────────────┘                        └────────┬─────────┘ │
└─────────────────────────────────────────────────────────────────────┘
                                                          │
                                                ┌─────────▼──────────┐
                                                │   Windows Input    │
                                                │  (pynput library)  │
                                                │  Keyboard + Mouse  │
                                                └────────────────────┘
```

### Alur Data

```
Android Touch Input
       │
       ▼
  Virtual Joystick / Button / Touchpad
       │
       ▼  JSON over WebSocket
  WebSocketServer (Python asyncio, background thread)
       │
       ├──► InputHandler
       │         ├── process_joystick()    → WASD key press/release
       │         ├── process_button()      → mapped keyboard key
       │         ├── process_dpad()        → directional key
       │         ├── process_mouse()       → mouse move (pynput)
       │         ├── process_mouse_click() → left/right click
       │         ├── process_mouse_scroll()→ scroll wheel
       │         └── process_skill_aim()  → aim + skill key tap
       │
       └──► UI Callbacks (Monitor log, Connection status update)
```

### Komponen Inti Server

```
main.py
  ├── load_settings()         # Baca / buat settings.json
  ├── WebSocketServer         # Asyncio WS server (background thread)
  ├── InputHandler            # Simulasi input via pynput
  └── AppWindow (CTk)         # GUI utama (main thread)
        ├── ConnectionPage    # Status server, QR Code
        ├── MonitorPage       # Log input real-time
        ├── ProfilesPage      # CRUD profil game
        ├── SettingsPage      # Konfigurasi runtime
        └── AboutPage         # Informasi aplikasi
```

---

## 📁 Struktur Folder

```
Controller projek/
│
├── 📁 Consensius Controller/           # Android App (Kotlin + Compose)
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   └── app/
│       ├── build.gradle.kts            # Konfigurasi build & dependensi
│       └── src/main/
│           ├── AndroidManifest.xml     # Permission: INTERNET, CAMERA
│           └── java/com/consensius/controller/
│               ├── MainActivity.kt         # Entry point, NavHost setup
│               ├── navigation/             # NavGraph & routing
│               ├── network/
│               │   └── WebSocketManager.kt # WebSocket client (OkHttp)
│               ├── input/                  # Input event builder
│               ├── model/                  # Data class (ControllerEvent, dll.)
│               ├── datastore/              # DataStore preferences
│               └── ui/
│                   ├── splash/             # Splash screen
│                   ├── home/               # Halaman utama
│                   ├── connection/         # Koneksi & QR scanner
│                   ├── controller/         # Layar controller (joystick & button)
│                   ├── profiles/           # Manajemen profil
│                   ├── settings/           # Pengaturan app
│                   └── theme/              # Warna, tipografi, tema
│
└── 📁 consensius-server/               # PC Server (Python)
    ├── main.py                         # Entry point: load settings, launch UI
    ├── requirements.txt                # Dependensi pip
    ├── settings.json                   # Konfigurasi runtime (auto-generated)
    │
    ├── network/
    │   └── websocket_server.py         # Asyncio WebSocket server
    │
    ├── core/
    │   ├── models.py                   # Dataclass: Profile, ControllerState, Config
    │   ├── state_manager.py            # Thread-safe controller state
    │   ├── config_manager.py           # Load/save konfigurasi
    │   ├── profile_manager.py          # CRUD profil game berbasis JSON
    │   ├── action_mapper.py            # Mapping tombol → keyboard action
    │   ├── input_engine.py             # Proses & publish input events
    │   ├── input_normalizer.py         # Normalisasi nilai joystick
    │   ├── event_bus.py                # Pub/sub event system internal
    │   ├── event_inspector.py          # Debug & inspeksi event
    │   ├── protocol_validator.py       # Validasi format pesan WebSocket
    │   └── logger.py                   # Custom formatter logger
    │
    ├── input/
    │   └── input_handler.py            # Simulasi keyboard & mouse via pynput
    │
    ├── executor/
    │   └── movement_simulator.py       # Simulasi gerakan WASD
    │
    ├── runtime/
    │   ├── event_logger.py             # Logging event ke file
    │   └── input_buffer.py             # Buffer untuk input async
    │
    ├── ui/
    │   ├── app_window.py               # Main window: sidebar nav, routing halaman
    │   ├── dashboard_window.py         # Dashboard utama
    │   ├── connection_page.py          # Status server & QR Code display
    │   ├── monitor_page.py             # Monitor input real-time
    │   ├── profiles_page.py            # UI CRUD profil
    │   ├── settings_page.py            # UI pengaturan lengkap
    │   ├── about_page.py               # Halaman tentang aplikasi
    │   └── widgets/                    # Komponen UI reusable
    │
    ├── utils/
    │   ├── ip_utils.py                 # Deteksi IP lokal PC
    │   └── qr_generator.py             # Generate QR Code untuk koneksi
    │
    ├── profiles/
    │   ├── mlbb.json                   # Profil Mobile Legends
    │   ├── pubg.json                   # Profil PUBG Mobile
    │   └── ff.json                     # Profil Free Fire
    │
    ├── config/
    │   └── config.json                 # Konfigurasi tambahan
    │
    └── logs/
        └── server.log                  # Log file (auto-generated)
```

---

## 📦 Protokol Komunikasi WebSocket

Server menerima pesan JSON melalui WebSocket. Berikut semua format pesan yang didukung:

### Joystick Movement
```json
{ "type": "joystick", "stick": "movement", "x": 0.5, "y": -0.3 }
```
> `stick` bisa bernilai `"movement"` / `"left"` untuk joystick kiri, atau `"right"` untuk joystick kanan.

### Button Press / Release
```json
{ "type": "button", "key": "skill1", "state": "down" }
{ "type": "button", "key": "skill1", "state": "up" }
```

### D-Pad
```json
{ "type": "dpad", "key": "up", "state": "down" }
```

### Mouse Move (Touchpad Drag)
```json
{ "type": "mouse_move", "dx": 12.5, "dy": -8.0 }
```

### Mouse Click (Touchpad Tap)
```json
{ "type": "mouse_click", "button": "left", "state": "down" }
{ "type": "mouse_click", "button": "right", "state": "up" }
```

### Mouse Scroll (2-Finger Drag)
```json
{ "type": "mouse_scroll", "dy": -1.0 }
```

### Skill Aim Joystick
```json
{
  "type": "skill_aim",
  "action": "skill1",
  "angle": 90.0,
  "magnitude": 0.85,
  "state": "cast"
}
```
> `state` bisa bernilai `"aiming"` (saat jari masih di layar) atau `"cast"` (saat jari dilepas, skill dieksekusi).

### Profile Sync
```json
{
  "type": "profile",
  "data": {
    "id": "mlbb",
    "name": "Mobile Legends",
    "bindings": { "skill1": "q", "attack": "space" }
  }
}
```

---

## 🗺️ Format Profil Game

Profil disimpan sebagai file `.json` di folder `profiles/`. Berikut contoh profil Mobile Legends:

```json
{
  "id": "mlbb",
  "name": "Mobile Legends",
  "bindings": {
    "skill1": "q",
    "skill2": "e",
    "skill3": "r",
    "attack": "space",
    "spell":  "f",
    "recall": "b",
    "regen":  "t"
  },
  "controller": {
    "deadzone": 0.25,
    "sensitivity": 1.0,
    "send_rate": 60
  },
  "metadata": {
    "created_at": "2026-06-23 18:45:00",
    "updated_at": "2026-06-23 18:45:00"
  }
}
```

Profil yang sudah tersedia:

| File | Game |
|---|---|
| `mlbb.json` | Mobile Legends: Bang Bang |
| `pubg.json` | PUBG Mobile |
| `ff.json` | Free Fire |

---

## ⚙️ Konfigurasi Server (`settings.json`)

File ini dibuat otomatis pada saat pertama kali server dijalankan.

```json
{
  "port": 8765,
  "start_on_launch": true,
  "mouse_sensitivity": 1.5,
  "joystick_threshold": 0.3,
  "invert_x": false,
  "invert_y": true,
  "skill_aim_distance": 800,
  "skill_positions": {
    "j": { "x": -1, "y": -1 },
    "k": { "x": -1, "y": -1 },
    "l": { "x": -1, "y": -1 },
    "u": { "x": -1, "y": -1 },
    "i": { "x": -1, "y": -1 },
    "f": { "x": -1, "y": -1 }
  }
}
```

| Key | Tipe | Default | Keterangan |
|---|---|---|---|
| `port` | int | `8765` | Port WebSocket server |
| `start_on_launch` | bool | `false` | Auto-start server saat dibuka |
| `mouse_sensitivity` | float | `1.0` | Sensitivitas gerakan mouse |
| `joystick_threshold` | float | `0.3` | Deadzone minimum joystick |
| `invert_x` | bool | `false` | Balik sumbu X gerakan mouse |
| `invert_y` | bool | `true` | Balik sumbu Y gerakan mouse |
| `skill_aim_distance` | int | `80` | Jarak pixel aim dari pusat layar |
| `skill_positions` | object | `{}` | Posisi pixel per skill (x=-1 berarti tengah layar) |

---

## 🚀 Instalasi & Cara Menjalankan

> 💡 **Pengguna biasa?** Tidak perlu install Python, Android Studio, atau tools apapun!
> Cukup download file di bawah ini dan langsung pakai.

---

### ⬇️ Download

**👉 [Klik di sini untuk download — GitHub Releases](https://github.com/ISDPRODUCTION/Consensius-Contoller/releases/latest)**

| File | Untuk | Ukuran |
|---|---|---|
| `ConsenciusServer-v1.0.0.exe` | 🖥️ PC / Laptop Windows | ~58 MB |
| `Consensius-Controller-v1.0.0.apk` | 📱 HP Android | ~31 MB |

---

### 🖥️ Cara Install di PC (Windows)

1. Download file **`ConsenciusServer-v1.0.0.exe`**
2. Simpan di folder mana saja (misal: Desktop)
3. **Klik dua kali** untuk membuka — tidak perlu install apapun
4. Jika muncul peringatan *"Windows protected your PC"*, klik **"More info"** → **"Run anyway"**
   > ⚠️ Peringatan ini muncul karena aplikasi belum memiliki sertifikat resmi, bukan karena berbahaya.
5. Klik tombol **▶ Start Server** di aplikasi

---

### 📱 Cara Install di HP Android

1. Download file **`Consensius-Controller-v1.0.0.apk`** langsung dari HP kamu
2. Buka file APK yang sudah didownload
3. Jika muncul peringatan **"Install from unknown sources"**:
   - Tap **"Settings"** → aktifkan **"Allow from this source"**
   - Kembali dan tap **"Install"**
4. Setelah terpasang, buka aplikasi **Consensius Controller**

> 📌 **Persyaratan:** Android minimal versi **8.0 (Oreo)** ke atas.

---

### 🔗 Cara Menghubungkan HP ke PC

1. Pastikan **PC dan HP terhubung ke Wi-Fi yang sama** (jaringan rumah/hotspot yang sama)
2. Buka **Consensius Server** di PC → klik **▶ Start Server**
3. Server akan menampilkan **QR Code** di layar
4. Buka aplikasi **Consensius Controller** di HP
5. Tap tombol **Scan QR** → arahkan kamera ke QR Code di layar PC
6. Koneksi terbuat otomatis ✅ — langsung siap main!

> **Alternatif tanpa QR:** Masukkan IP PC secara manual di aplikasi Android.
> IP bisa dilihat di aplikasi server (contoh: `192.168.1.5`).

---

### 👨‍💻 Setup untuk Developer (Build dari Source)

<details>
<summary>Klik untuk expand — panduan build manual</summary>

#### Prasyarat

| Komponen | Versi Minimum |
|---|---|
| Python | 3.11+ |
| Android Studio | Ladybug / terbaru |
| JDK | 17+ |
| Koneksi Jaringan | PC dan Android di Wi-Fi yang sama |

#### 🖥️ Setup Server (PC)

**1. Clone repository**
```bash
git clone https://github.com/ISDPRODUCTION/Consensius-Contoller.git
cd Consensius-Contoller/consensius-server
```

**2. (Opsional) Buat virtual environment**
```bash
python -m venv .venv

# Windows
.venv\Scripts\activate

# Linux / macOS
source .venv/bin/activate
```

**3. Install dependensi**
```bash
pip install -r requirements.txt
```

**4. Jalankan server**
```bash
python main.py
```

> **Catatan Windows:** Jika mouse/keyboard tidak merespons, coba jalankan sebagai **Administrator**.

#### 📱 Build APK Android

**Metode 1: Android Studio**
1. Buka **Android Studio** → **Open** → pilih folder `Consensius Controller/`
2. Tunggu **Gradle Sync** selesai
3. Hubungkan HP via USB (aktifkan Developer Mode + USB Debugging)
4. Klik **Run ▶**

**Metode 2: Command Line**
```bash
cd "Consensius Controller"
gradlew.bat assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

</details>

---

### 🔗 Cara Menghubungkan Android ke Server

1. Pastikan **PC dan Android terhubung ke Wi-Fi yang sama**
2. Jalankan **Consensius Server** di PC → klik **Start Server**
3. Server akan menampilkan **QR Code** beserta IP dan port
4. Buka aplikasi **Consensius Controller** di Android
5. Tap tombol **Scan QR** dan arahkan kamera ke QR Code di layar PC
6. Koneksi terbuat secara otomatis ✅

> **Alternatif:** Masukkan IP PC secara manual di aplikasi Android dengan format `ws://192.168.x.x:8765`

---

## 🎮 Panduan Penggunaan

### Kontrol Controller

| Kontrol | Fungsi |
|---|---|
| **Joystick Kiri** | Gerak karakter (WASD) |
| **Joystick Kanan** | Gerak kamera / mouse |
| **Tombol Skill** | Disesuaikan dengan profil aktif |
| **Touchpad – Drag** | Gerak mouse |
| **Touchpad – 1 Tap** | Klik kiri mouse |
| **Touchpad – 2 Tap** | Klik kanan mouse |
| **Touchpad – 2 Jari Drag** | Scroll mouse |
| **D-Pad** | Navigasi / aksi tambahan |

### Membuat Profil Baru

1. Buka tab **Profiles** di server
2. Klik **New Profile** → isi **ID** (alphanumeric, lowercase) dan **Nama**
3. Atur key binding sesuai game (contoh: `skill1 → q`)
4. Klik **Save** → profil tersimpan sebagai `profiles/<id>.json`

### Kalibrasi Skill Aim

1. Buka **Settings → Skill Aim Positions** di server
2. Klik pada posisi skill di layar game untuk mendaftarkan koordinat
3. Atur **Skill Aim Distance** sesuai radius aim yang diinginkan

---

## 🛠️ Teknologi yang Digunakan

### Server (Python)

| Library | Versi | Fungsi |
|---|---|---|
| `websockets` | ≥ 12.0 | Asyncio WebSocket server |
| `pynput` | ≥ 1.7.6 | Simulasi keyboard & mouse |
| `customtkinter` | ≥ 5.2.0 | GUI desktop modern |
| `qrcode[pil]` | ≥ 7.4.2 | Generate QR Code |
| `Pillow` | ≥ 10.0.0 | Image processing |

### Android (Kotlin)

| Library | Fungsi |
|---|---|
| Jetpack Compose + Material 3 | UI deklaratif modern |
| OkHttp WebSocket | WebSocket client |
| Gson | Serialisasi/deserialisasi JSON |
| CameraX | Akses kamera untuk QR scan |
| ML Kit Barcode Scanning | Decode QR Code |
| DataStore Preferences | Penyimpanan preferensi persisten |
| Navigation Compose | Routing antar screen |
| Accompanist SystemUI | Immersive fullscreen mode |

---

## 🔍 Troubleshooting

### Android tidak bisa connect ke server
- ✅ Pastikan PC dan Android di jaringan Wi-Fi yang **sama**
- ✅ Pastikan Windows Firewall mengizinkan koneksi di port **8765**
- ✅ Coba nonaktifkan sementara antivirus atau firewall pihak ketiga
- ✅ Periksa IP PC dengan `ipconfig` di Command Prompt dan masukkan manual

### Keyboard/mouse tidak merespons di game
- ✅ Jalankan `main.py` sebagai **Administrator** di Windows
- ✅ Pastikan jendela game berada di **foreground** (tidak diminimize)
- ✅ Periksa tab **Monitor** di server untuk memastikan input diterima

### Server crash atau error saat startup
- ✅ Pastikan Python versi **3.11+**: jalankan `python --version`
- ✅ Install ulang dependensi: `pip install -r requirements.txt --force-reinstall`
- ✅ Hapus `settings.json` agar dibuat ulang dengan nilai default

### QR Code tidak bisa di-scan
- ✅ Pastikan izin **Kamera** sudah diberikan ke aplikasi Android
- ✅ Perbesar ukuran jendela server agar QR Code lebih besar dan jelas
- ✅ Kurangi pencahayaan di sekitar layar monitor untuk mengurangi glare

### Latency/lag terasa besar
- ✅ Hubungkan PC ke router via **kabel LAN** alih-alih Wi-Fi
- ✅ Pastikan Android berada dalam jangkauan sinyal Wi-Fi yang kuat
- ✅ Tutup aplikasi berat lainnya di PC untuk membebaskan sumber daya

---

## 📄 Lisensi

Proyek ini dilisensikan di bawah **MIT License** — bebas digunakan, dimodifikasi, dan didistribusikan dengan menyertakan atribusi.

---

<div align="center">

Dibuat oleh tim **ISD.PRODUCTION**

</div>
