param(
    [int[]]$Keys,
    [string]$OutFile
)

& adb connect 192.168.8.210:5555
Start-Sleep -Milliseconds 400
foreach ($k in $Keys) {
    & adb -s 192.168.8.210:5555 shell input keyevent $k
    Start-Sleep -Milliseconds 300
}
Start-Sleep -Milliseconds 500
& adb -s 192.168.8.210:5555 shell screencap -p /sdcard/temp_screen.png
& adb -s 192.168.8.210:5555 pull /sdcard/temp_screen.png $OutFile
