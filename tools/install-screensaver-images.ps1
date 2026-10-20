[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Device,
    [string]$Source = "D:\Wallpapers"
)
$ErrorActionPreference = "Stop"
$sourceFolder = Get-Item -LiteralPath $Source
if (-not $sourceFolder.PSIsContainer) { throw "Source must be an image directory" }
$images = @(Get-ChildItem -LiteralPath $sourceFolder.FullName -File | Where-Object { $_.Extension -in '.jpg', '.jpeg', '.png', '.webp' })
if ($images.Count -eq 0) { throw "No supported images found" }
$destination = '/sdcard/Android/data/com.mihir.tvhome/files/screensaver'
& adb -s $Device shell mkdir -p $destination
if ($LASTEXITCODE -ne 0) { throw "Cannot prepare the app image folder" }
foreach ($photo in $images) {
    & adb -s $Device push $photo.FullName "$destination/$($photo.Name)"
    if ($LASTEXITCODE -ne 0) { throw "Image transfer failed: $($photo.Name)" }
}
Write-Output "Copied $($images.Count) images. Source files are unchanged. Restart the screensaver to use them."
