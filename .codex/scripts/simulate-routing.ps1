param(
    [switch]$Validate
)

$routes = @(
    [pscustomobject]@{
        Class = "A"
        Scenario = "Localizar arquivo, simbolo ou flag de overlay"
        Agents = "explorer"
        Models = "gpt-5.6-luna:low"
        MaxAgents = 1
        UsesSol = $false
    },
    [pscustomobject]@{
        Class = "B"
        Scenario = "Ajuste delimitado de Kotlin, Compose, parser ou teste"
        Agents = "android_worker/ui_compose/ocr_parser + optional tester"
        Models = "gpt-5.6-terra:medium"
        MaxAgents = 2
        UsesSol = $false
    },
    [pscustomobject]@{
        Class = "C"
        Scenario = "Mudanca transversal, lifecycle, contrato, privacidade, release ou bug sem causa clara"
        Agents = "architect/debugger/privacy_security/release_gate as needed + implementer/tester"
        Models = "gpt-5.6-sol:max/high + gpt-5.6-terra:medium/high"
        MaxAgents = 4
        UsesSol = $true
    }
)

if ($Validate) {
    $errors = @()
    $classA = $routes | Where-Object { $_.Class -eq "A" }
    if ($classA.UsesSol -or $classA.Models -match "sol") {
        $errors += "Class A must not use Sol."
    }
    foreach ($route in $routes) {
        if ($route.MaxAgents -gt 4) {
            $errors += "Class $($route.Class) exceeds the four-agent normal limit."
        }
    }
    if (($routes | Where-Object { $_.Agents -match "explorer" }).Count -eq 0) {
        $errors += "Explorer route is missing."
    }
    if (($routes | Where-Object { $_.Class -eq "C" }).Agents -notmatch "privacy_security") {
        $errors += "Class C sensitive route must include privacy_security."
    }
    if ($errors.Count -gt 0) {
        $errors | ForEach-Object { Write-Error $_ }
        exit 1
    }
    Write-Output "Routing simulation OK"
}

$routes | Format-Table -AutoSize
