param(
    [string]$OutFile = "screen.png"
)

& adb connect 192.168.8.210:5555
Start-Sleep -Milliseconds 400
& adb -s 192.168.8.210:5555 shell screencap -p /sdcard/temp_screen.png
& adb -s 192.168.8.210:5555 pull /sdcard/temp_screen.png $OutFile
