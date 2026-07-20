param(
    [switch]$Hook
)

$ErrorActionPreference = "Stop"

function Get-RepoRoot {
    $root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
    return $root.Path
}

function Complete-Failure {
    param([string]$Message)

    if ($Hook) {
        @{
            continue = $false
            stopReason = "Driver Inteligente policy violation"
            systemMessage = $Message
        } | ConvertTo-Json -Compress
        exit 0
    }

    Write-Error $Message
    exit 1
}

function Assert-Contains {
    param(
        [string]$Path,
        [string]$Needle,
        [string]$Message
    )

    if (-not (Test-Path $Path)) {
        Complete-Failure "Required file is missing: $Path"
    }
    $content = Get-Content -Raw $Path
    if (-not $content.Contains($Needle)) {
        Complete-Failure $Message
    }
}

function Assert-NotContainsInFile {
    param(
        [string]$Path,
        [string]$Needle,
        [string]$Message
    )

    if (Test-Path $Path) {
        $content = Get-Content -Raw $Path
        if ($content.Contains($Needle)) {
            Complete-Failure $Message
        }
    }
}

function Assert-NotContainsInTree {
    param(
        [string]$Path,
        [string[]]$Needles
    )

    if (-not (Test-Path $Path)) {
        Complete-Failure "Required source tree is missing: $Path"
    }

    $files = Get-ChildItem -Path $Path -Recurse -File
    foreach ($file in $files) {
        $content = Get-Content -Raw $file.FullName
        foreach ($needle in $Needles) {
            if ($content.Contains($needle)) {
                Complete-Failure "Forbidden token '$needle' found in $($file.FullName)."
            }
        }
    }
}

$repo = Get-RepoRoot
$manifest = Join-Path $repo "app\src\main\AndroidManifest.xml"
$javaRoot = Join-Path $repo "app\src\main\java"
$overlay = Join-Path $repo "app\src\main\java\br\com\nexo\driver\overlay\WindowManagerOfferOverlay.kt"
$accessibilityConfig = Join-Path $repo "app\src\main\res\xml\driver_accessibility_service.xml"
$projectionService = Join-Path $repo "app\src\main\java\br\com\nexo\driver\capture\OfferCaptureService.kt"
$backupRules = Join-Path $repo "app\src\main\res\xml\backup_rules.xml"
$dataRules = Join-Path $repo "app\src\main\res\xml\data_extraction_rules.xml"
$gradleFiles = @(
    (Join-Path $repo "app\build.gradle.kts"),
    (Join-Path $repo "gradle\libs.versions.toml")
)

$internetLines = Select-String -Path $manifest -Pattern "android.permission.INTERNET" -ErrorAction SilentlyContinue
foreach ($line in $internetLines) {
    if ($line.Line -notmatch 'tools:node="remove"') {
        throw "Manifest may only mention INTERNET when removing a transitive permission: $($line.Line.Trim())"
    }
}
$networkStateLines = Select-String -Path $manifest -Pattern "android.permission.ACCESS_NETWORK_STATE" -ErrorAction SilentlyContinue
foreach ($line in $networkStateLines) {
    if ($line.Line -notmatch 'tools:node="remove"') {
        throw "Manifest may only mention ACCESS_NETWORK_STATE when removing a transitive permission: $($line.Line.Trim())"
    }
}
$mergedManifests = Get-ChildItem -Path (Join-Path $repo "app\build\intermediates") -Recurse -Filter AndroidManifest.xml -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -match "\\process.*Manifest\\" -or $_.FullName -match "\\merged_manifest\\" }
foreach ($mergedManifest in $mergedManifests) {
    Assert-NotContainsInFile $mergedManifest.FullName "android.permission.INTERNET" "Merged manifest must not declare INTERNET."
    Assert-NotContainsInFile $mergedManifest.FullName "android.permission.ACCESS_NETWORK_STATE" "Merged manifest must not declare ACCESS_NETWORK_STATE."
}
Assert-Contains $manifest 'android:allowBackup="false"' "Application backup must stay disabled."
Assert-NotContainsInFile $manifest "android.permission.ACCESS_BACKGROUND_LOCATION" "Background location is forbidden."
Assert-NotContainsInTree $javaRoot @(
    "performAction",
    "dispatchGesture",
    "ACTION_CLICK",
    "sendPointerSync",
    "injectInputEvent",
    "UiDevice.click(",
    "OfferHistoryStore",
    "SharedPreferencesOfferHistory",
    "openFileOutput(",
    "FileOutputStream(",
    "FileWriter(",
    "writeText(",
    "writeBytes(",
    "Bitmap.compress("
)
Assert-NotContainsInTree $javaRoot @(
    "Log.d(TAG, rawText",
    "Log.i(TAG, rawText",
    "Log.v(TAG, rawText",
    'putString("raw_ocr"',
    'putString("offer_address"'
)
Assert-Contains (Join-Path $repo "app\src\main\java\br\com\nexo\driver\accessibility\DriverAccessibilityService.kt") "AccessibilityService" "Read-only AccessibilityService must remain explicit and auditable."
Assert-Contains $accessibilityConfig 'android:packageNames="com.ubercab.driver,com.app99.driver"' "Accessibility reading must stay scoped to Uber and 99."
Assert-NotContainsInFile $accessibilityConfig "canPerformGestures" "Accessibility gestures are forbidden."
Assert-Contains $overlay "FLAG_NOT_TOUCHABLE" "Overlay must remain non-touchable."
Assert-Contains $overlay "FLAG_SECURE" "Overlay must remain FLAG_SECURE to avoid capture feedback."
Assert-Contains $projectionService "projection?.stop()" "MediaProjection must be stopped when capture ends."
Assert-Contains $projectionService "reader?.close()" "MediaProjection ImageReader must be closed when capture ends."
Assert-Contains $projectionService "Intent.ACTION_SCREEN_OFF" "MediaProjection must stop when the screen is locked."
Assert-Contains $projectionService "onTaskRemoved" "MediaProjection must stop when the app task is removed."

foreach ($domain in @("root", "file", "database", "sharedpref", "external")) {
    Assert-Contains $backupRules "domain=`"$domain`"" "backup_rules.xml must exclude domain '$domain'."
    Assert-Contains $dataRules "domain=`"$domain`"" "data_extraction_rules.xml must exclude domain '$domain'."
}

foreach ($file in $gradleFiles) {
    Assert-NotContainsInFile $file "okhttp" "Network dependency okhttp is not allowed without explicit approval."
    Assert-NotContainsInFile $file "retrofit" "Network dependency retrofit is not allowed without explicit approval."
    Assert-NotContainsInFile $file "ktor" "Network dependency ktor is not allowed without explicit approval."
    Assert-NotContainsInFile $file "firebase" "Firebase dependency is not allowed without explicit approval."
    Assert-NotContainsInFile $file "crashlytics" "Crashlytics dependency is not allowed without explicit approval."
}

if (-not $Hook) {
    Write-Output "Driver Inteligente policy guard OK"
}
