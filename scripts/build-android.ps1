param(
    [ValidateSet("Debug", "Release")]
    [string]$Variant = "Debug",
    [string]$JavaHome,
    [string]$GomobileAndroidHome,
    [switch]$SkipBind
)

$ErrorActionPreference = "Stop"

function Resolve-ExistingPath {
    param(
        [string]$ExplicitValue,
        [string[]]$EnvironmentNames,
        [Parameter(Mandatory = $true)][string]$Name,
        [switch]$Required
    )

    $candidates = @()
    if (![string]::IsNullOrWhiteSpace($ExplicitValue)) {
        $candidates += $ExplicitValue
    }
    foreach ($envName in $EnvironmentNames) {
        $envValue = [Environment]::GetEnvironmentVariable($envName)
        if (![string]::IsNullOrWhiteSpace($envValue)) {
            $candidates += $envValue
        }
    }

    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) {
            return (Resolve-Path $candidate).Path
        }
    }

    if ($Required) {
        $hint = if ($EnvironmentNames.Count -gt 0) {
            " Pass -$Name or set " + ($EnvironmentNames -join "/") + "."
        } else {
            " Pass -$Name."
        }
        throw "$Name not found.$hint"
    }

    return $null
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$androidDir = Join-Path $repoRoot "android"
$bindScript = Join-Path $PSScriptRoot "gomobile-bind.ps1"
$gradleWrapper = Join-Path $androidDir "gradlew.bat"

if (!(Test-Path $gradleWrapper)) {
    throw "Gradle wrapper not found: $gradleWrapper"
}

$resolvedAndroidHome = Resolve-ExistingPath `
    -ExplicitValue $GomobileAndroidHome `
    -EnvironmentNames @("ANDROID_HOME", "ANDROID_SDK_ROOT") `
    -Name "GomobileAndroidHome" `
    -Required:(!$SkipBind)
$resolvedJavaHome = Resolve-ExistingPath `
    -ExplicitValue $JavaHome `
    -EnvironmentNames @("JAVA_HOME") `
    -Name "JavaHome"

if (!$SkipBind) {
    if (!(Test-Path $bindScript)) {
        throw "Bind script not found: $bindScript"
    }

    $bindArgs = @("-AndroidHome", $resolvedAndroidHome)
    if ($resolvedJavaHome) {
        $bindArgs += @("-JavaHome", $resolvedJavaHome)
    }
    & $bindScript @bindArgs
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}

if ($resolvedJavaHome) {
    $env:JAVA_HOME = $resolvedJavaHome
    $env:Path = ((Join-Path $resolvedJavaHome "bin"), $env:Path) -join [System.IO.Path]::PathSeparator
}

$task = if ($Variant -eq "Release") { "assembleRelease" } else { "assembleDebug" }
Push-Location $androidDir
try {
    & $gradleWrapper $task
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}
finally {
    Pop-Location
}
