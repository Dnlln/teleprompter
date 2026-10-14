$ErrorActionPreference = "Stop"

$javaHome = "C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
$env:JAVA_HOME = $javaHome
$env:PATH = "$javaHome\bin;$env:PATH"

Set-Location "c:\Projects\teleprompter-android"
& "C:\Android\gradle-8.5\bin\gradle.bat" assembleDebug --no-daemon

$src = "c:\Projects\teleprompter-android\app\build\outputs\apk\debug\app-debug.apk"
if (Test-Path $src) {
    Copy-Item $src "c:\Projects\teleprompter.apk" -Force
    Copy-Item $src "c:\Projects\teleprompter-web\teleprompter.apk" -Force
    $item = Get-Item "c:\Projects\teleprompter.apk"
    $sizeMb = [math]::Round($item.Length / 1MB, 2)
    Write-Output "REBUILD SUCCESSFUL!"
    Write-Output "APK Updated: c:\Projects\teleprompter.apk"
    Write-Output "APK Size: $sizeMb MB"
} else {
    Write-Error "Build failed: APK not found"
}
