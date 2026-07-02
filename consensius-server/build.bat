@echo off
REM ================================================================
REM  build.bat  –  Build Consensius Server as standalone .exe
REM  Run this file from the consensius-server directory.
REM ================================================================

echo.
echo  =========================================
echo   Consensius Server — EXE Builder
echo  =========================================
echo.

REM Install dependencies if not installed
echo [1/3] Checking dependencies...
pip install -r requirements.txt --quiet
pip install pyinstaller --quiet

REM Clean previous build
echo [2/3] Cleaning previous build...
if exist "dist\ConsenciusServer.exe" (
    del /f /q "dist\ConsenciusServer.exe"
    echo      Previous build removed.
)
if exist "build" (
    rmdir /s /q "build"
)

REM Build the EXE
echo [3/3] Building EXE with PyInstaller...
pyinstaller consensius_server.spec --clean

echo.
if exist "dist\ConsenciusServer.exe" (
    echo  [SUCCESS] Build complete!
    echo  Output: dist\ConsenciusServer.exe
) else (
    echo  [ERROR] Build failed. Check the output above for errors.
    exit /b 1
)

echo.
pause
