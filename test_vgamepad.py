import vgamepad as vg
import time
import math

print("Creating gamepad...")
gamepad = vg.VX360Gamepad()
time.sleep(1)  # Wait for device to be ready

print("Moving left joystick in a circle using float...")
for i in range(36):
    angle = math.radians(i * 10)
    x = math.cos(angle)
    y = math.sin(angle)
    gamepad.left_joystick_float(x_value_float=x, y_value_float=y)
    gamepad.update()
    time.sleep(0.05)

print("Moving right joystick in a circle using int...")
for i in range(36):
    angle = math.radians(i * 10)
    x = int(math.cos(angle) * 32767)
    y = int(math.sin(angle) * 32767)
    gamepad.right_joystick(x_value=x, y_value=y)
    gamepad.update()
    time.sleep(0.05)

print("Resetting...")
gamepad.reset()
gamepad.update()
print("Done.")