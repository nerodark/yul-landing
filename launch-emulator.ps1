# launch-emulator.ps1 - start the AVD for the YUL Landing app.
#
# Run from the project folder:   .\launch-emulator.ps1
#
# It uses the toolchain that .\update.ps1 sets up under .\tools (JDK +
# Android SDK). If the emulator package or the AVD's system image is not
# installed in .\tools\android-sdk yet, it installs them, then launches the
# "hello" AVD and waits until it finishes booting. The emulator keeps running
# after this script exits, so a subsequent .\update.ps1 can deploy to it.

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$toolsRoot = Join-Path $scriptDir "tools"
$jdk       = Join-Path $toolsRoot "jdk"
$sdk       = Join-Path $toolsRoot "android-sdk"
$adb       = Join-Path $sdk      "platform-tools\adb.exe"
$emulator  = Join-Path $sdk      "emulator\emulator.exe"
$avdName   = "hello"

# 1. Toolchain (set up by update.ps1)
if (-not (Test-Path (Join-Path $jdk "bin\java.exe"))) {
    throw "JDK not found in $jdk - run .\update.ps1 first"
}
if (-not (Test-Path $adb)) {
    throw "Android SDK not found in $sdk - run .\update.ps1 first"
}
$env:JAVA_HOME    = $jdk
$env:ANDROID_HOME = $sdk

# 2. Ensure the emulator package + the AVD's system image are installed
$sdkmanager = Join-Path $sdk "cmdline-tools\bin\sdkmanager.bat"
if (-not (Test-Path $emulator)) {
    Write-Host "==> Installing emulator package"
    & $sdkmanager --sdk_root=$sdk "emulator" | Out-Null
}
$imageDir = Join-Path $sdk "system-images\android-35\google_apis\x86_64"
if (-not (Test-Path $imageDir)) {
    Write-Host "==> Installing system image (android-35 google_apis x86_64)"
    & $sdkmanager --sdk_root=$sdk "system-images;android-35;google_apis;x86_64" | Out-Null
}
if (-not (Test-Path $emulator)) { throw "emulator.exe not found after install" }

# 3. Launch the AVD (detached; it stays running after this script exits)
Write-Host ("==> Launching AVD '{0}'" -f $avdName)
Start-Process -FilePath $emulator -ArgumentList "-avd", $avdName

# 4. Wait for the device to appear and finish booting
Write-Host "==> Waiting for device to boot (this can take a few minutes)"
$prev = $ErrorActionPreference
$ErrorActionPreference = "Continue"
& $adb wait-for-device 2>$null
$boot = ""
for ($i = 0; $i -lt 180; $i++) {
    $boot = (& $adb shell getprop sys.boot_completed 2>$null | ForEach-Object { $_.Trim() })
    if ($boot -eq "1") { break }
    Start-Sleep -Seconds 1
}
$ErrorActionPreference = $prev
if ($boot -ne "1") { throw "Emulator did not finish booting" }
Write-Host ("==> Emulator '{0}' is ready" -f $avdName)
Write-Host "==> Now run .\update.ps1 to build + deploy"
