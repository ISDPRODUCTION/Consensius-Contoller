import vgamepad as vg
import time

print("Init gamepad")
gamepad = vg.VX360Gamepad()
time.sleep(1) # wait for Windows to detect

print("Pushing left stick UP and RIGHT (32767, 32767)")
gamepad.left_joystick(x_value=32767, y_value=32767)
gamepad.right_joystick(x_value=32767, y_value=32767)
gamepad.update()
time.sleep(2)

print("Pushing left stick DOWN and LEFT (-32768, -32768)")
gamepad.left_joystick(x_value=-32768, y_value=-32768)
gamepad.right_joystick(x_value=-32768, y_value=-32768)
gamepad.update()
time.sleep(2)

gamepad.reset()
gamepad.update()
print("Done")