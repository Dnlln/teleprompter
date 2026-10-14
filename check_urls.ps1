$r1 = Invoke-WebRequest -Uri "https://services.gradle.org/distributions/gradle-8.5-bin.zip" -Method Head -UseBasicParsing
$r2 = Invoke-WebRequest -Uri "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip" -Method Head -UseBasicParsing
Write-Output "Gradle URL Status: $($r1.StatusCode)"
Write-Output "Android Cmdline URL Status: $($r2.StatusCode)"
