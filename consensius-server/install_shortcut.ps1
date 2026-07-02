# ============================================================
#  install_shortcut.ps1
#  Daftarkan ConsenciusServer ke Start Menu Windows
#  sehingga bisa dicari di Windows Search.
#
#  Cara pakai:
#    Klik kanan file ini → "Run with PowerShell"
#    ATAU jalankan di terminal:
#    powershell -ExecutionPolicy Bypass -File install_shortcut.ps1
# ============================================================

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "  =================================================" -ForegroundColor Cyan
Write-Host "   Consensius Server — Start Menu Installer" -ForegroundColor Cyan
Write-Host "  =================================================" -ForegroundColor Cyan
Write-Host ""

# ── Resolve path ke .exe relatif dari lokasi script ini ─────────────────────
$scriptDir   = Split-Path -Parent $MyInvocation.MyCommand.Definition
$exePath     = Join-Path $scriptDir "dist\ConsenciusServer.exe"
$iconPath    = Join-Path $scriptDir "assets\icon.ico"

# ── Validasi .exe ada ────────────────────────────────────────────────────────
if (-not (Test-Path $exePath)) {
    Write-Host "  [ERROR] File .exe tidak ditemukan:" -ForegroundColor Red
    Write-Host "          $exePath" -ForegroundColor Red
    Write-Host ""
    Write-Host "  Jalankan build.bat terlebih dahulu untuk membuat .exe." -ForegroundColor Yellow
    Write-Host ""
    Read-Host "  Tekan Enter untuk keluar"
    exit 1
}

# ── Tentukan lokasi shortcut di Start Menu ───────────────────────────────────
$startMenuDir  = "$env:APPDATA\Microsoft\Windows\Start Menu\Programs"
$shortcutPath  = Join-Path $startMenuDir "ConsenciusServer.lnk"

# ── Buat shortcut ─────────────────────────────────────────────────────────────
Write-Host "  [1/2] Membuat shortcut di Start Menu..." -ForegroundColor White

$shell    = New-Object -ComObject WScript.Shell
$shortcut = $shell.CreateShortcut($shortcutPath)
$shortcut.TargetPath       = $exePath
$shortcut.WorkingDirectory = (Split-Path $exePath -Parent)
$shortcut.Description      = "Consensius Server"

# Pakai icon dari assets jika tersedia, fallback ke icon bawaan .exe
if (Test-Path $iconPath) {
    $shortcut.IconLocation = "$iconPath,0"
} else {
    $shortcut.IconLocation = "$exePath,0"
}

$shortcut.Save()

Write-Host "  [2/2] Shortcut berhasil dibuat!" -ForegroundColor Green
Write-Host ""
Write-Host "  Lokasi : $shortcutPath" -ForegroundColor Gray
Write-Host ""
Write-Host "  Cara pakai:" -ForegroundColor Cyan
Write-Host "    1. Tekan tombol Windows" -ForegroundColor White
Write-Host "    2. Ketik 'Consensius'" -ForegroundColor White
Write-Host "    3. Klik aplikasinya" -ForegroundColor White
Write-Host ""
Write-Host "  =================================================" -ForegroundColor Cyan
Write-Host "   Selesai! Aplikasi sudah terdaftar di Windows." -ForegroundColor Green
Write-Host "  =================================================" -ForegroundColor Cyan
Write-Host ""

Read-Host "  Tekan Enter untuk keluar"
