[CmdletBinding()]
param(
    [ValidateSet("build", "install")]
    [string]$Mode = "build",
    [string]$Device = ""
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
Set-Location $ScriptDir

$Sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME }
       elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT }
       elseif (Test-Path "C:\Users\moham\AppData\Local\Android\Sdk") { "C:\Users\moham\AppData\Local\Android\Sdk" }
       else { Join-Path $HOME "AppData\Local\Android\Sdk" }

if (-not (Test-Path $Sdk)) {
    throw "Android SDK not found at $Sdk. Set ANDROID_HOME environment variable."
}

$BuildToolsDir = Get-ChildItem -Directory "$Sdk\build-tools" | Sort-Object Name -Descending | Select-Object -First 1
if (-not $BuildToolsDir) { throw "No build-tools found in $Sdk\build-tools" }
$Bt = $BuildToolsDir.FullName

$PlatformDir = Get-ChildItem -Directory "$Sdk\platforms" | Sort-Object Name -Descending | Select-Object -First 1
if (-not $PlatformDir) { throw "No platforms found in $Sdk\platforms" }
$Jar = Join-Path $PlatformDir.FullName "android.jar"

$Aapt2 = Join-Path $Bt "aapt2.exe"
$D8 = Join-Path $Bt "d8.bat"
$Zipalign = Join-Path $Bt "zipalign.exe"
$Apksigner = Join-Path $Bt "apksigner.bat"
$MinSdk = 28
$TargetSdk = 28

Write-Host "Using Android SDK: $Sdk"
Write-Host "Using Build-Tools: $($BuildToolsDir.Name)"
Write-Host "Using Platform: $($PlatformDir.Name)"

$BuildDir = Join-Path $ScriptDir "build"
if (Test-Path $BuildDir) { Remove-Item $BuildDir -Recurse -Force }
$GenDir = Join-Path $BuildDir "gen"
$ClassesDir = Join-Path $BuildDir "classes"
$DexDir = Join-Path $BuildDir "dex"
New-Item -ItemType Directory -Path $GenDir, $ClassesDir, $DexDir -Force | Out-Null

$ResZip = Join-Path $BuildDir "res.zip"
$BaseApk = Join-Path $BuildDir "base.apk"
$UnsignedApk = Join-Path $BuildDir "unsigned.apk"
$AlignedApk = Join-Path $BuildDir "aligned.apk"
$FinalApk = Join-Path $BuildDir "tvhome-starnet-android9.apk"

Write-Host "[1/6] Compiling resources with aapt2..."
& $Aapt2 compile --dir res -o $ResZip
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

Write-Host "[2/6] Linking resources with aapt2..."
& $Aapt2 link -o $BaseApk -I $Jar --manifest AndroidManifest.xml --java $GenDir `
    -A assets --min-sdk-version $MinSdk --target-sdk-version $TargetSdk $ResZip
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

Write-Host "[3/6] Compiling Java sources with javac..."
$JavaFiles = (Get-ChildItem -Path "src", $GenDir -Recurse -Filter "*.java").FullName
& javac -nowarn -Xlint:-options --release 11 -classpath $Jar -d $ClassesDir $JavaFiles
if ($LASTEXITCODE -ne 0) { throw "javac compilation failed" }

Write-Host "[4/6] Converting classes to DEX with d8..."
$ClassFiles = (Get-ChildItem -Path $ClassesDir -Recurse -Filter "*.class").FullName
& cmd.exe /c "`"$D8`" --min-api $MinSdk --lib `"$Jar`" --output `"$DexDir`" $($ClassFiles -join ' ')"
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

Write-Host "[5/6] Packaging and aligning APK..."
Copy-Item $BaseApk $UnsignedApk

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open($UnsignedApk, [System.IO.Compression.ZipArchiveMode]::Update)
$dexFile = Join-Path $DexDir "classes.dex"
[System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $dexFile, "classes.dex")
$zip.Dispose()

& $Zipalign -f 4 $UnsignedApk $AlignedApk
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

Write-Host "[6/6] Signing APK..."
$Keytool = if (Get-Command keytool -ErrorAction SilentlyContinue) { "keytool" }
           elseif ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\keytool.exe")) { "$env:JAVA_HOME\bin\keytool.exe" }
           else {
               $found = Get-ChildItem "C:\Program Files\Java" -Recurse -Filter "keytool.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
               if ($found) { $found.FullName } else { "keytool" }
           }

$Keystore = Join-Path $ScriptDir "debug.keystore"
if (-not (Test-Path $Keystore)) {
    & $Keytool -genkeypair -keystore $Keystore -storepass android -keypass android `
        -alias tvhome -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=StarNet tv-home test"
}

& cmd.exe /c "`"$Apksigner`" sign --v1-signing-enabled true --v2-signing-enabled true --ks `"$Keystore`" --ks-pass pass:android --out `"$FinalApk`" `"$AlignedApk`""
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }

Write-Host "Success! Built: $FinalApk" -ForegroundColor Green

if ($Mode -eq "install") {
    Write-Host "Installing to device..."
    $adbArgs = if ($Device) { @("-s", $Device) } else { @() }
    & adb @adbArgs devices
    & adb @adbArgs install -r $FinalApk
    & adb @adbArgs shell am start -n com.mihir.tvhome/.MainActivity
    Write-Host "Test app launched. Stock home and satellite services unchanged." -ForegroundColor Green
}
