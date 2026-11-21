param([string]$JavaExecutable = 'java', [string]$Namespaces = '')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$vmArgs = Join-Path $projectRoot 'build/moddev/gameTestServerRunVmArgs.txt'
$programArgs = Join-Path $projectRoot 'build/moddev/gameTestServerRunProgramArgs.txt'
$legacyClasspath = Join-Path $projectRoot 'build/moddev/gameTestServerLegacyClasspath.txt'
$gameJar = Join-Path $projectRoot 'build/moddev/artifacts/neoforge-21.1.228.jar'
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
if ($Namespaces -split ',' -contains 'starfall_blueprint_esper') {
    $testDirectory = Join-Path $projectRoot ('run/baiwan-gametest-' + [guid]::NewGuid().ToString('N'))
    $datapacks = Join-Path $testDirectory 'world/datapacks'
    New-Item -ItemType Directory -Path $datapacks -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'baiwan-test-dimensions') -Destination $datapacks -Recurse
}
New-Item -ItemType Directory -Path $testDirectory -Force | Out-Null
Push-Location $testDirectory
try {
    # Use the official prepared bootstrap arguments directly. No DevLaunch download is needed.
    if ($Namespaces) {
        & $JavaExecutable -cp $classpath "@$vmArgs" "-Dneoforge.enabledGameTestNamespaces=$Namespaces" "@$programArgs"
    } else {
        & $JavaExecutable -cp $classpath "@$vmArgs" "@$programArgs"
    }
    $testResult = $LASTEXITCODE
} finally {
    Pop-Location
    $env:MOD_CLASSES = $previousModClasses
}
exit $testResult
