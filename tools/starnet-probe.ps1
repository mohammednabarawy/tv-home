# Read-only ADB diagnostics for StarNet. Never changes system state.
$ErrorActionPreference = "Stop"
if (-not (Get-Command adb -ErrorAction SilentlyContinue)) { throw "Install Android Platform Tools (adb) first." }
adb devices
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$out = Join-Path (Get-Location) "starnet-diagnostics-$stamp"
New-Item -ItemType Directory -Path $out -Force | Out-Null
$checks = @(
  @{Name="01-android-version.txt"; Args=@("shell","getprop","ro.build.version.release")},
  @{Name="02-api-level.txt"; Args=@("shell","getprop","ro.build.version.sdk")},
  @{Name="03-device-model.txt"; Args=@("shell","getprop","ro.product.model")},
  @{Name="04-manufacturer.txt"; Args=@("shell","getprop","ro.product.manufacturer")},
  @{Name="05-platform.txt"; Args=@("shell","getprop","ro.board.platform")},
  @{Name="06-cpu-abi.txt"; Args=@("shell","getprop","ro.product.cpu.abi")},
  @{Name="06b-android-build.txt"; Args=@("shell","getprop","ro.build.display.id")},
  @{Name="06c-android-incremental.txt"; Args=@("shell","getprop","ro.build.version.incremental")},
  @{Name="06d-security-patch.txt"; Args=@("shell","getprop","ro.build.version.security_patch")},
  @{Name="07-packages.txt"; Args=@("shell","pm","list","packages")},
  @{Name="08-user-apps.txt"; Args=@("shell","pm","list","packages","-3")},
  @{Name="09-home-resolution.txt"; Args=@("shell","cmd","package","resolve-activity","--brief","-a","android.intent.action.MAIN","-c","android.intent.category.HOME")},
  @{Name="10-launcher-activities.txt"; Args=@("shell","cmd","package","query-activities","--brief","-a","android.intent.action.MAIN","-c","android.intent.category.LAUNCHER")},
  @{Name="11-home-activities.txt"; Args=@("shell","cmd","package","query-activities","--brief","-a","android.intent.action.MAIN","-c","android.intent.category.HOME")},
  @{Name="12-current-activity.txt"; Args=@("shell","dumpsys","activity","activities")},
  @{Name="13-window-focus.txt"; Args=@("shell","dumpsys","window","windows")}
)
foreach ($check in $checks) {
  & adb @($check.Args) 2>&1 | Out-File -FilePath (Join-Path $out $check.Name) -Encoding utf8
}
Write-Host "Read-only diagnostics saved to $out."
Write-Host "Files 12 and 13 can be long; look for mResumedActivity or mCurrentFocus to identify the StarNet/Glide app."
Write-Host "Run while the device is at its ORIGINAL Glide home screen to capture useful foreground activity."
Write-Host "Review/redact package and device information before sharing; do not share identifiers or activation data."
