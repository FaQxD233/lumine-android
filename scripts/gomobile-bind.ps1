param(
    [string]$AndroidHome,
    [string]$JavaHome,
    [string]$Output = "android\app\libs\LumineCore.aar",
    [int]$AndroidApi = 24,
    [string]$BuildTags = "godns",
    [string]$Package = "./mobile"
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

function Backup-File {
    param([string]$Path)
    if (!(Test-Path $Path)) {
        return $null
    }
    $tmp = [System.IO.Path]::GetTempFileName()
    Copy-Item $Path $tmp -Force
    return $tmp
}

function Restore-File {
    param(
        [string]$Backup,
        [string]$Destination
    )
    if ($Backup -and (Test-Path $Backup)) {
        Copy-Item $Backup $Destination -Force
        Remove-Item $Backup -Force
    }
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$resolvedOutput = if ([System.IO.Path]::IsPathRooted($Output)) {
    $Output
} else {
    Join-Path $repoRoot $Output
}

$resolvedAndroidHome = Resolve-ExistingPath `
    -ExplicitValue $AndroidHome `
    -EnvironmentNames @("ANDROID_HOME", "ANDROID_SDK_ROOT") `
    -Name "AndroidHome" `
    -Required
$resolvedJavaHome = Resolve-ExistingPath `
    -ExplicitValue $JavaHome `
    -EnvironmentNames @("JAVA_HOME") `
    -Name "JavaHome"

$goModPath = Join-Path $repoRoot "go.mod"
$goSumPath = Join-Path $repoRoot "go.sum"
$goModBackup = Backup-File $goModPath
$goSumBackup = Backup-File $goSumPath

try {
    $env:ANDROID_HOME = $resolvedAndroidHome
    $env:ANDROID_SDK_ROOT = $resolvedAndroidHome
    if ($resolvedJavaHome) {
        $env:JAVA_HOME = $resolvedJavaHome
    }
    $env:GOFLAGS = "-mod=mod"
    $pathEntries = @()
    if ($resolvedJavaHome) {
        $pathEntries += (Join-Path $resolvedJavaHome "bin")
    }
    $pathEntries += (Join-Path $resolvedAndroidHome "platform-tools")
    $env:Path = ($pathEntries + $env:Path) -join [System.IO.Path]::PathSeparator

    $outDir = Split-Path -Parent $resolvedOutput
    if ($outDir -and !(Test-Path $outDir)) {
        New-Item -ItemType Directory -Path $outDir | Out-Null
    }

    $gomobileArgs = @(
        "bind"
        "-target=android"
        "-androidapi"
        "$AndroidApi"
    )
    if (![string]::IsNullOrWhiteSpace($BuildTags)) {
        $gomobileArgs += @("-tags", $BuildTags)
    }
    $gomobileArgs += @(
        "-o"
        $resolvedOutput
        $Package
    )

    Push-Location $repoRoot
    try {
        & gomobile @gomobileArgs
    }
    finally {
        Pop-Location
    }
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}
finally {
    Restore-File -Backup $goModBackup -Destination $goModPath
    Restore-File -Backup $goSumBackup -Destination $goSumPath
}
