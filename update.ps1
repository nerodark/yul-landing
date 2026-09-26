# update.ps1 - portable build (and optional deploy) for the YUL Landing app.
#
# Copy this file into the project folder (next to the .csproj and index.html)
# and run from anywhere:   .\update.ps1
#
# It will:
#   1. Use a bundled tools.zip next to this script if present (JDK 17 + Android SDK).
#   2. Otherwise reuse an existing JAVA_HOME / ANDROID_HOME.
#   3. Otherwise download JDK 17 + Android SDK.
#
# The toolchain is stored inside the project (under .\tools). A sibling
# Directory.Build.props excludes tools\** from the build's item globs, so an
# in-project android-sdk does not trigger "XA1014 identical file names but
# different contents" failures.
#
# Requires the .NET 10 SDK on PATH. If a device/emulator is connected, the APK
# is installed and launched; otherwise the APK path is printed.
#
# Optional: .\update.ps1 -Device <name> to target a specific device when
# several are connected. Accepts an AVD name (tablet / phone) or a serial
# from `adb devices`.
#
# By default the script only deploys the newest existing APK. Pass -Build to
# build first:   .\update.ps1 -Build

param([string]$Device = "", [switch]$Build)

$ErrorActionPreference = "Stop"

# Invoke-WebRequest downloads are slow if not set
$ProgressPreference = "SilentlyContinue"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$project   = $scriptDir
$toolsZip  = Join-Path $scriptDir "tools.zip"
$toolsRoot = Join-Path $scriptDir "tools"
$jdk       = Join-Path $toolsRoot "jdk"
$sdk       = Join-Path $toolsRoot "android-sdk"
$adb       = Join-Path $sdk      "platform-tools\adb.exe"

# 0. Sanity: find the csproj (first one in the project dir)
$csproj = Get-ChildItem -LiteralPath $project -Filter *.csproj -File | Select-Object -First 1
if (-not $csproj) { throw "No .csproj found in $project" }

# Package name (Android ApplicationId) read from the csproj so the launch
# step stays in sync if you change it there.
$package = (Select-String -Path $csproj.FullName -Pattern '<ApplicationId>.*?</ApplicationId>' |
    ForEach-Object { $_.Line -replace '<ApplicationId>','' -replace '</ApplicationId>','' } |
    ForEach-Object { $_.Trim() })
if (-not $package) { throw "No <ApplicationId> found in $($csproj.FullName)" }

# 1. .NET SDK
if (-not (Get-Command dotnet -ErrorAction SilentlyContinue)) {
    throw ".NET SDK not found on PATH - install it from https://dotnet.microsoft.com/download"
}
Write-Host ("==> dotnet {0}" -f (dotnet --version))

# 2. JDK 17
$javaExe = Join-Path $jdk "bin\java.exe"
$haveJdk = Test-Path $javaExe
if (-not $haveJdk -and (Test-Path $toolsZip)) {
    Write-Host "==> Extracting bundled tools.zip (JDK + Android SDK) to $toolsRoot"
    # The archive is laid out as tools\jdk + tools\android-sdk; expanding it
    # straight into the project dir yields .\tools\{jdk,android-sdk}.
    Expand-Archive $toolsZip -DestinationPath $scriptDir -Force
    $haveJdk = Test-Path $javaExe
}
if (-not $haveJdk -and $env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
    Write-Host ("==> Using JAVA_HOME: {0}" -f $env:JAVA_HOME)
    $jdk = $env:JAVA_HOME
}
if (-not (Test-Path (Join-Path $jdk "bin\java.exe"))) {
    New-Item -ItemType Directory -Path $jdk -Force | Out-Null
    $zip = Join-Path $toolsRoot "jdk17.zip"
    Write-Host "==> Downloading JDK 17 (Temurin, ~190 MB)"
    Invoke-WebRequest "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse" -OutFile $zip
    Expand-Archive $zip -DestinationPath $jdk -Force
    Remove-Item $zip
    $inner = Get-ChildItem -LiteralPath $jdk -Directory | Select-Object -First 1
    if ($inner) { Move-Item (Join-Path $inner.FullName "*") $jdk; Remove-Item $inner.FullName }
}
Write-Host ("==> JDK: {0}" -f (Join-Path $jdk "bin\java.exe"))
$env:JAVA_HOME = $jdk

# 3. Android SDK (platform-tools + build-tools 36.0.0 + android-35/36)
$haveSdk = Test-Path $adb
if (-not $haveSdk -and (Test-Path $toolsZip)) {
    $haveSdk = Test-Path $adb
}
if (-not $haveSdk -and $env:ANDROID_HOME -and (Test-Path (Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"))) {
    Write-Host ("==> Using ANDROID_HOME: {0}" -f $env:ANDROID_HOME)
    $sdk = $env:ANDROID_HOME
}
if (-not (Test-Path (Join-Path $sdk "platform-tools\adb.exe"))) {
    New-Item -ItemType Directory -Path $sdk -Force | Out-Null
    $cmdlineZip = Join-Path $toolsRoot "cmdline-tools.zip"
    Write-Host "==> Downloading Android command-line tools"
    Invoke-WebRequest "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip" -OutFile $cmdlineZip
    Expand-Archive $cmdlineZip -DestinationPath $toolsRoot -Force
    Remove-Item $cmdlineZip
    $cm = Join-Path $sdk "cmdline-tools"
    if (Test-Path $cm) { Remove-Item -LiteralPath $cm -Recurse -Force }
    Move-Item (Join-Path $toolsRoot "cmdline-tools") $cm
    $sdkmanager = Join-Path $cm "bin\sdkmanager.bat"
    $env:ANDROID_HOME = $sdk
    Write-Host "==> Installing platform-tools, build-tools;36.0.0, android-35, android-36"
    # --licenses shows one prompt per package; send enough "y" lines to accept all.
    (1..15 | ForEach-Object { "y" }) | & $sdkmanager --sdk_root=$sdk --licenses | Out-Null
    & $sdkmanager --sdk_root=$sdk "platform-tools" "build-tools;36.0.0" "platforms;android-35" "platforms;android-36" | Out-Null
}

# 4. Build (only when -Build is passed; toolchain env vars scoped to this process)
$env:JAVA_HOME    = $jdk
$env:ANDROID_HOME = $sdk
if ($Build) {
    Write-Host "==> Building"
    dotnet build $csproj.FullName -c Release --no-incremental `
        -p:JavaSdkDirectory=$jdk -p:AndroidSdkDirectory=$sdk |
        Select-String "error|Build succeeded|Build FAILED" | ForEach-Object { Write-Host "    $($_.Line)" }
}
# Locate the APK dynamically (name depends on the csproj/assembly name).
$apk = Get-ChildItem -LiteralPath $project -Recurse -Filter *.apk -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -match '\\Release\\' } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $apk) {
    throw "No APK found - run .\update.ps1 -Build first"
}
if ($Build -and $apk.LastWriteTime -lt (Get-Date).AddMinutes(-1)) {
    throw "Build did not produce a fresh APK"
}
$apk = $apk.FullName
Write-Host ("    APK: {0}" -f $apk)

# 5. Deploy: first online adb device/emulator, if any
# adb prints informational text to stderr (e.g. "daemon not running; starting
# now"), which would abort under $ErrorActionPreference="Stop", so relax it
# for the whole deploy phase.
$ErrorActionPreference = "Continue"
$devices = & $adb devices 2>$null | Select-String "^\S+\s+device$" | ForEach-Object { ($_.Line -split "\s+")[0] }
if (-not $devices) {
    Write-Host "==> No device connected - APK only:"
    Write-Host ("    {0}" -f $apk)
    return
}
if ($Device) {
    if (@($devices) -contains $Device) {
        $dev = $Device
    } else {
        # Treat it as an AVD name (e.g. 'tablet'/'phone') and find the serial
        $dev = ""
        $connected = @()
        foreach ($s in $devices) {
            $name = (& $adb -s $s emu avd name 2>$null | Select-Object -First 1 | ForEach-Object { ($_ -split "\s+")[0] })
            if ($name) { $connected += "$name ($s)" } else { $connected += $s }
            if ($name -eq $Device) { $dev = $s; break }
        }
        if (-not $dev) { throw "Device '$Device' not found. Connected: $($connected -join ', ')" }
    }
} else {
    $dev = @($devices)[0]
}
Write-Host ("==> Installing on {0}" -f $dev)
& $adb -s $dev install -r $apk | ForEach-Object { Write-Host "    $_" }

Write-Host "==> Launching"
$prev = $ErrorActionPreference
$ErrorActionPreference = "Continue"
& $adb -s $dev shell monkey -p $package -c android.intent.category.LAUNCHER 1 2>&1 |
    Select-String "Events injected" | ForEach-Object { Write-Host "    $($_.Line)" }
$ErrorActionPreference = $prev

Write-Host "==> Done"
