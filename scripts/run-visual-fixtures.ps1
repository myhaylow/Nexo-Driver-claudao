$ErrorActionPreference = "Stop"

$repo = Split-Path -Parent $PSScriptRoot
$releaseService = "br.com.nexo.driver/br.com.nexo.driver.accessibility.DriverAccessibilityService"

try {
    Push-Location $repo
    & .\gradlew.bat connectedDebugAndroidTest `
        "-Pandroid.testInstrumentationRunnerArguments.class=br.com.nexo.driver.ocr.mlkit.OfferScreenshotFixtureTest"
    if ($LASTEXITCODE -ne 0) {
        throw "Visual fixture tests failed with exit code $LASTEXITCODE."
    }
} finally {
    Pop-Location
    # Samsung/Android may clear the enabled-service setting when the temporary debug package is
    # removed. Restore only the already-installed signed release service; no APK is reinstalled.
    & adb shell settings put secure enabled_accessibility_services $releaseService
    & adb shell settings put secure accessibility_enabled 1
    & adb shell appops set br.com.nexo.driver android:system_alert_window allow
}
