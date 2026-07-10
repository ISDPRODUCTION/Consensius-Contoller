"""
Debug script: Test langsung apakah vgamepad bisa menggerakkan analog
yang terdeteksi di hardwaretester.com/gamepad
Jalankan script ini, buka hardwaretester.com/gamepad, dan lihat apakah analog bergerak.
"""
import vgamepad as vg
import time
import math

print("=== VGAMEPAD ANALOG DEBUG TEST ===")
print("1. Buka https://hardwaretester.com/gamepad di browser")
print("2. Tunggu 3 detik, lalu lihat apakah analog bergerak...")
print()

gamepad = vg.VX360Gamepad()
time.sleep(2)

print("[TEST 1] Left stick ke kanan penuh (X=1.0, Y=0.0)...")
gamepad.left_joystick_float(x_value_float=1.0, y_value_float=0.0)
gamepad.update()
time.sleep(1.5)

print("[TEST 2] Left stick ke kiri penuh (X=-1.0, Y=0.0)...")
gamepad.left_joystick_float(x_value_float=-1.0, y_value_float=0.0)
gamepad.update()
time.sleep(1.5)

print("[TEST 3] Left stick ke atas (X=0.0, Y=1.0)...")
gamepad.left_joystick_float(x_value_float=0.0, y_value_float=1.0)
gamepad.update()
time.sleep(1.5)

print("[TEST 4] Left stick ke bawah (X=0.0, Y=-1.0)...")
gamepad.left_joystick_float(x_value_float=0.0, y_value_float=-1.0)
gamepad.update()
time.sleep(1.5)

print("[TEST 5] Right stick ke kanan penuh (X=1.0, Y=0.0)...")
gamepad.left_joystick_float(x_value_float=0.0, y_value_float=0.0)
gamepad.right_joystick_float(x_value_float=1.0, y_value_float=0.0)
gamepad.update()
time.sleep(1.5)

print("[TEST 6] Right stick ke kiri penuh (X=-1.0, Y=0.0)...")
gamepad.right_joystick_float(x_value_float=-1.0, y_value_float=0.0)
gamepad.update()
time.sleep(1.5)

print("[TEST 7] Putar melingkar LEFT stick...")
for i in range(72):
    angle = math.radians(i * 5)
    gamepad.left_joystick_float(x_value_float=math.cos(angle), y_value_float=math.sin(angle))
    gamepad.update()
    time.sleep(0.05)

print("[TEST 8] Putar melingkar RIGHT stick...")
for i in range(72):
    angle = math.radians(i * 5)
    gamepad.right_joystick_float(x_value_float=math.cos(angle), y_value_float=math.sin(angle))
    gamepad.update()
    time.sleep(0.05)

gamepad.reset()
gamepad.update()
print()
print("=== SELESAI ===")
print("Apakah analog bergerak di hardwaretester.com/gamepad? (y/n): ", end="")
