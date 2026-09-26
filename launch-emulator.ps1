# launch-emulator.ps1 - start a phone or tablet AVD for the YUL Landing app.
#
# Run from the project folder:
#   .\launch-emulator.ps1               # phone (default)
#   .\launch-emulator.ps1 -Device phone
#
# It uses the toolchain that .\update.ps1 sets up under .\tools (JDK +
# Android SDK). If the emulator package or the system image is not installed
# in .\tools\android-sdk yet, it installs them. The AVDs live in .\tools\avd
# (ANDROID_AVD_HOME) so the whole setup stays inside the project folder.
# Then it launches the requested AVD and waits until it finishes booting.
# The emulator keeps running after this script exits, so a subsequent
# .\update.ps1 can deploy to it.

param(
    [ValidateSet('tablet', 'phone')]
    [string]$Device = 'phone'
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$toolsRoot = Join-Path $scriptDir "tools"
$jdk       = Join-Path $toolsRoot "jdk"
$sdk       = Join-Path $toolsRoot "android-sdk"
$adb       = Join-Path $sdk      "platform-tools\adb.exe"
$emulator  = Join-Path $sdk      "emulator\emulator.exe"

# AVD name -> hardware profile, for each supported form factor
$devices = @{ tablet = 'pixel_tablet'; phone = 'pixel_7' }
$avdName   = $Device
$avdDevice = $devices[$Device]

# 1. Toolchain (set up by update.ps1)
if (-not (Test-Path (Join-Path $jdk "bin\java.exe"))) {
    throw "JDK not found in $jdk - run .\update.ps1 first"
}
if (-not (Test-Path $adb)) {
    throw "Android SDK not found in $sdk - run .\update.ps1 first"
}
$env:JAVA_HOME    = $jdk
$env:ANDROID_HOME = $sdk

# Keep the AVDs inside the project so the whole setup is self-contained
$avdHome = Join-Path $toolsRoot "avd"
New-Item -ItemType Directory -Path $avdHome -Force | Out-Null
$env:ANDROID_AVD_HOME = $avdHome

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

# 2b. Ensure all supported AVDs exist (creates any that are missing) and
#     point at a valid system image
$avdmanager = Join-Path $sdk "cmdline-tools\bin\avdmanager.bat"
$imagePath  = (Join-Path $sdk "system-images\android-35\google_apis\x86_64").Replace('\', '\\')
$list = & $avdmanager list avd 2>$null
foreach ($name in $devices.Keys) {
    $config = Join-Path $avdHome ("{0}.avd\config.ini" -f $name)
    if (-not ($list | Select-String -Pattern "Name:\s+$name\b")) {
        Write-Host ("==> Creating AVD '{0}' ({1})" -f $name, $devices[$name])
        echo no | & $avdmanager create avd -n $name -k "system-images;android-35;google_apis;x86_64" -d $devices[$name] | Out-Null
    }
    # avdmanager records a broken relative image path; point it at the absolute one
    if (Test-Path $config) {
        $cfg = Get-Content $config -Raw
        if ($cfg -notmatch [regex]::Escape($imagePath)) {
            $cfg -replace 'image\.sysdir\.1=.*', "image.sysdir.1=$imagePath\" | Set-Content $config
        }
    }
}

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
    $serials = & $adb devices 2>$null | Select-String "^\S+\s+device$" | ForEach-Object { ($_.Line -split "\s+")[0] }
    foreach ($s in $serials) {
        $b = (& $adb -s $s shell getprop sys.boot_completed 2>$null | ForEach-Object { $_.Trim() })
        if ($b -eq "1") { $boot = "1"; break }
    }
    if ($boot -eq "1") { break }
    Start-Sleep -Seconds 1
}
$ErrorActionPreference = $prev
if ($boot -ne "1") { throw "Emulator did not finish booting" }
Write-Host ("==> Emulator '{0}' is ready" -f $avdName)
Write-Host "==> Now run .\update.ps1 to build + deploy"
