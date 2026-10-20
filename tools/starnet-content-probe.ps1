# Extended content & media capability probe for StarNet Twin 4000 Plus
param(
    [string]$Device = "192.168.8.210:5555"
)

$ErrorActionPreference = "Continue"

Write-Host "Connecting to $Device..."
adb connect $Device

$outDir = "tools/content-probe-results"
if (Test-Path $outDir) { Remove-Item -Recurse -Force $outDir }
New-Item -ItemType Directory -Path $outDir -Force | Out-Null

function Run-AdbQuery([string]$filename, [string[]]$adbArgs) {
    Write-Host "Running: adb -s $Device $($adbArgs -join ' ')"
    $filePath = Join-Path $outDir $filename
    & adb -s $Device @adbArgs 2>&1 | Out-File -FilePath $filePath -Encoding utf8
}

# 1. System info
Run-AdbQuery "01-sdk.txt" @("shell", "getprop", "ro.build.version.sdk")
Run-AdbQuery "02-display-id.txt" @("shell", "getprop", "ro.build.display.id")
Run-AdbQuery "03-model.txt" @("shell", "getprop", "ro.product.model")

# 2. Installed packages
Run-AdbQuery "04-user-packages.txt" @("shell", "pm", "list", "packages", "-3")
Run-AdbQuery "05-all-packages.txt" @("shell", "pm", "list", "packages")

# 3. Android TV Content Infrastructure (TvContract)
Run-AdbQuery "06-tv-channels.txt" @("shell", "content", "query", "--uri", "content://android.media.tv/channel")
Run-AdbQuery "07-tv-preview-programs.txt" @("shell", "content", "query", "--uri", "content://android.media.tv/preview_program")
Run-AdbQuery "08-tv-watch-next.txt" @("shell", "content", "query", "--uri", "content://android.media.tv/watch_next_program")
Run-AdbQuery "09-tv-programs.txt" @("shell", "content", "query", "--uri", "content://android.media.tv/program")
Run-AdbQuery "10-tv-inputs.txt" @("shell", "dumpsys", "tv_input")

# 4. Content Providers declared on system
Run-AdbQuery "11-providers.txt" @("shell", "dumpsys", "package", "providers")

# 5. Target IPTV & Media Packages Dumpsys
$targetPkgs = @(
    "com.logicyel.novastb",
    "com.marvelactivecode.marvel",
    "com.datesplus.iptv",
    "com.tosmart.glide",
    "io.gh.reisxd.tizentube.fortaleza",
    "org.xbmc.kodi",
    "com.stremio.one",
    "org.jellyfin.androidtv",
    "com.netflix.mediaclient",
    "com.google.android.youtube.tv",
    "com.google.android.youtube",
    "com.tosmart.filemanagerpro",
    "com.excellence.appstore",
    "com.esaba.downloader",
    "com.mxtech.videoplayer.pro"
)

foreach ($pkg in $targetPkgs) {
    Run-AdbQuery "pkg-$pkg.txt" @("shell", "dumpsys", "package", $pkg)
}

# 6. Check open network ports (for Kodi JSON-RPC, local servers, etc.)
Run-AdbQuery "12-netstat.txt" @("shell", "netstat", "-tlpn")

Write-Host "Probe completed. Results in $outDir."
