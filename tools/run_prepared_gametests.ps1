param([string]$JavaExecutable = 'java')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$vmArgs = Join-Path $projectRoot 'build/moddev/gameTestServerRunVmArgs.txt'
$programArgs = Join-Path $projectRoot 'build/moddev/gameTestServerRunProgramArgs.txt'
$legacyClasspath = Join-Path $projectRoot 'build/moddev/gameTestServerLegacyClasspath.txt'
$gameJar = Join-Path $projectRoot 'build/moddev/artifacts/neoforge-21.1.262.jar'
foreach ($required in @($vmArgs, $programArgs, $legacyClasspath, $gameJar)) {
    if (!(Test-Path -LiteralPath $required)) {
        throw "Missing prepared runtime: $required. Run the Gradle prepareGameTestServerRun task first."
    }
}
$modClasses = Join-Path $projectRoot 'build/classes/java/main'
$modResources = Join-Path $projectRoot 'build/resources/main'
$classpath = (@($gameJar, $modClasses, $modResources) + (Get-Content -LiteralPath $legacyClasspath)) -join [IO.Path]::PathSeparator
$previousModClasses = $env:MOD_CLASSES
$env:MOD_CLASSES = "starfall_blueprint%%$modClasses$([IO.Path]::PathSeparator)starfall_blueprint%%$modResources"
$testDirectory = Join-Path $projectRoot 'run/gametest'
New-Item -ItemType Directory -Path $testDirectory -Force | Out-Null
Push-Location $testDirectory
try {
    # Use the official prepared bootstrap arguments directly. No DevLaunch download is needed.
    & $JavaExecutable -cp $classpath "@$vmArgs" "@$programArgs"
    $testResult = $LASTEXITCODE
} finally {
    Pop-Location
    $env:MOD_CLASSES = $previousModClasses
}
exit $testResult
