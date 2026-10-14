$ErrorActionPreference = "Stop"

$baseDir = "C:\Android"
if (!(Test-Path $baseDir)) { New-Item -ItemType Directory -Path $baseDir -Force | Out-Null }

$javaHome = "C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
$env:JAVA_HOME = $javaHome
$env:PATH = "$javaHome\bin;$env:PATH"

Write-Output "[1/5] Checking Gradle 8.5..."
$gradleDir = Join-Path $baseDir "gradle-8.5"
$gradleBin = Join-Path $gradleDir "bin\gradle.bat"

if (!(Test-Path $gradleBin)) {
    $gradleZip = Join-Path $baseDir "gradle-8.5-bin.zip"
    if (!(Test-Path $gradleZip)) {
        Write-Output "Downloading Gradle 8.5..."
        Invoke-WebRequest -Uri "https://services.gradle.org/distributions/gradle-8.5-bin.zip" -OutFile $gradleZip -UseBasicParsing
    }
    Write-Output "Extracting Gradle 8.5..."
    Expand-Archive -Path $gradleZip -DestinationPath $baseDir -Force
    if (Test-Path $gradleZip) { Remove-Item $gradleZip -Force }
}
Write-Output "Gradle ready: $gradleBin"

Write-Output "[2/5] Checking Android SDK Command-Line Tools..."
$sdkDir = Join-Path $baseDir "Sdk"
$cmdlineLatest = Join-Path $sdkDir "cmdline-tools\latest"
$sdkManager = Join-Path $cmdlineLatest "bin\sdkmanager.bat"

if (!(Test-Path $sdkManager)) {
    $cmdlineZip = Join-Path $baseDir "commandlinetools.zip"
    if (!(Test-Path $cmdlineZip)) {
        Write-Output "Downloading Android Command-Line Tools..."
        Invoke-WebRequest -Uri "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip" -OutFile $cmdlineZip -UseBasicParsing
    }
    Write-Output "Extracting Command-Line Tools..."
    $tempExtract = Join-Path $baseDir "cmdline-temp"
    if (Test-Path $tempExtract) { Remove-Item -Recurse -Force $tempExtract }
    Expand-Archive -Path $cmdlineZip -DestinationPath $tempExtract -Force

    $cmdlineDir = Join-Path $sdkDir "cmdline-tools"
    if (!(Test-Path $cmdlineDir)) { New-Item -ItemType Directory -Path $cmdlineDir -Force | Out-Null }
    if (Test-Path $cmdlineLatest) { Remove-Item -Recurse -Force $cmdlineLatest }

    Move-Item -Path (Join-Path $tempExtract "cmdline-tools") -Destination $cmdlineLatest -Force
    Remove-Item -Recurse -Force $tempExtract
    if (Test-Path $cmdlineZip) { Remove-Item $cmdlineZip -Force }
}
Write-Output "Android SDK Manager ready: $sdkManager"

Write-Output "[3/5] Setting up licenses and installing Android SDK 34..."
$env:ANDROID_HOME = $sdkDir
$env:ANDROID_SDK_ROOT = $sdkDir

$licensesDir = Join-Path $sdkDir "licenses"
if (!(Test-Path $licensesDir)) { New-Item -ItemType Directory -Path $licensesDir -Force | Out-Null }

$androidLicense = "24333f8a63061b2c300903f6e7bb1432140402e0`nd56f5187479451eabf01fb78af6dfcb131a6481e`n84831b9409646a2b3e44449b329de40a04295d91"
$androidPreviewLicense = "84831b9409646a2b3e44449b329de40a04295d91"

[System.IO.File]::WriteAllText((Join-Path $licensesDir "android-sdk-license"), $androidLicense)
[System.IO.File]::WriteAllText((Join-Path $licensesDir "android-sdk-preview-license"), $androidPreviewLicense)

Write-Output "Installing platforms;android-34 and build-tools;34.0.0..."
& $sdkManager --sdk_root=$sdkDir "platforms;android-34" "build-tools;34.0.0" "platform-tools"

Write-Output "[4/5] Writing local.properties..."
$localProps = "c:\Projects\teleprompter-android\local.properties"
$escapedSdk = $sdkDir.Replace("\", "\\")
Set-Content -Path $localProps -Value "sdk.dir=$escapedSdk"
Write-Output "Configured $localProps"

Write-Output "[5/5] Building APK (gradle assembleDebug)..."
Push-Location "c:\Projects\teleprompter-android"
try {
    & $gradleBin assembleDebug --no-daemon
} finally {
    Pop-Location
}

$sourceApk = "c:\Projects\teleprompter-android\app\build\outputs\apk\debug\app-debug.apk"
$targetApk = "c:\Projects\teleprompter.apk"

if (Test-Path $sourceApk) {
    Copy-Item $sourceApk $targetApk -Force
    $fileItem = Get-Item $targetApk
    $sizeMb = [math]::Round($fileItem.Length / 1MB, 2)
    Write-Output "BUILD SUCCESSFUL!"
    Write-Output "APK Location: $targetApk"
    Write-Output "APK Size: $sizeMb MB"
} else {
    Write-Error "APK file was not created!"
}
