# Set variables
$gradleDistFolder = "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.13-bin"
$gradleDistUniqueFolder = Get-ChildItem $gradleDistFolder | Select-Object -First 1
$gradleDistPath = Join-Path $gradleDistFolder $gradleDistUniqueFolder

$gradleZipUrl = "https://services.gradle.org/distributions/gradle-8.13-bin.zip"
$gradleZipPath = "$env:TEMP\gradle-8.13-bin.zip"
$extractPath = $gradleDistPath + "\gradle-8.13"

Write-Host "1. Deleting existing Gradle distribution folder..."
if (Test-Path $gradleDistPath) {
    Remove-Item -Recurse -Force $gradleDistPath
    Write-Host "Deleted: $gradleDistPath"
} else {
    Write-Host "Folder does not exist, skipping deletion."
}

Write-Host "2. Downloading Gradle 8.13 distribution..."
Invoke-WebRequest -Uri $gradleZipUrl -OutFile $gradleZipPath

Write-Host "3. Extracting Gradle zip to distribution folder..."
New-Item -ItemType Directory -Path $extractPath -Force | Out-Null
Expand-Archive -Path $gradleZipPath -DestinationPath $extractPath -Force

Write-Host "4. Removing downloaded zip file..."
Remove-Item -Force $gradleZipPath

Write-Host "5. Running Gradle clean build with refreshed dependencies..."
# Change to your project directory if running externally
# cd "D:\BatteryMonitor"
# Run gradlew.bat (adjust path if needed)
& .\gradlew.bat clean build --refresh-dependencies

Write-Host "✅ Done! Gradle distribution refreshed and build attempted."
