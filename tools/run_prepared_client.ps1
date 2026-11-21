param(
    [string]$JavaExecutable = 'java',
    [string]$GameDirectory = '',
    [string]$QuickPlayWorld = '',
    [switch]$LingchuanVisualCheck,
    [switch]$BlackDogPlaytest,
    [switch]$BlackDogWorkshopCheck,
    [switch]$BlackDogPolishCheck,
    [switch]$BlackDogLockMarkerCheck,
    [switch]$HaijiaVisualCheck,
    [switch]$BestCockpitCheck,
    [switch]$BestMechVisualCheck,
    [switch]$ShowWindow,
    [switch]$EsperVisualCheck,
    [switch]$HaijiaFlightCheck
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (!$GameDirectory) { $GameDirectory = Join-Path $projectRoot 'run' }
$vmArgs = Join-Path $projectRoot 'build/moddev/clientRunVmArgs.txt'
$programArgs = Join-Path $projectRoot 'build/moddev/clientRunProgramArgs.txt'
$legacyClasspath = Join-Path $projectRoot 'build/moddev/clientLegacyClasspath.txt'
$gameJar = Join-Path $projectRoot 'build/moddev/artifacts/neoforge-21.1.228.jar'
foreach ($required in @($vmArgs, $programArgs, $legacyClasspath, $gameJar)) {
    if (!(Test-Path -LiteralPath $required)) { throw "Missing prepared client runtime: $required. Run classes prepareClientRun first." }
}
New-Item -ItemType Directory -Path $GameDirectory -Force | Out-Null
$gameDirectoryPath = (Resolve-Path -LiteralPath $GameDirectory).Path
$modClasses = Join-Path $projectRoot 'build/classes/java/main'
$modResources = Join-Path $projectRoot 'build/resources/main'
$clientClasspath = (@($gameJar, $modClasses, $modResources) + (Get-Content -LiteralPath $legacyClasspath)) -join [IO.Path]::PathSeparator
$previousModClasses = $env:MOD_CLASSES
$env:MOD_CLASSES = "starfall_blueprint%%$modClasses$([IO.Path]::PathSeparator)starfall_blueprint%%$modResources"
try {
    $arguments = @('-Xmx4G', '-cp', ('"' + $clientClasspath + '"'), ('"@' + $vmArgs + '"'), ('"@' + $programArgs + '"'))
    if ($QuickPlayWorld) {
        $arguments += @('--quickPlaySingleplayer', ('"' + $QuickPlayWorld + '"'))
    }
    if ($LingchuanVisualCheck) {
        $arguments = @('-Dstarfall.lingchuanVisualCheck=true') + $arguments + @('--width', '1280', '--height', '800')
    }
    if ($BlackDogPlaytest) {
        $arguments = @('-Dstarfall.blackdogPlaytest=true') + $arguments + @('--width', '1440', '--height', '900')
    }
    if ($BlackDogWorkshopCheck) {
        $arguments = @('-Dstarfall.blackdogWorkshopCheck=true') + $arguments + @('--width', '1440', '--height', '900')
    }
    if ($BlackDogPolishCheck) {
        $arguments = @('-Dstarfall.blackdogPolishCheck=true') + $arguments + @('--width', '1440', '--height', '900')
    }
    if ($BlackDogLockMarkerCheck) {
        $arguments = @('-Dstarfall.blackdogLockMarkerCheck=true') + $arguments + @('--width', '1440', '--height', '900')
    }
    if ($HaijiaVisualCheck) {
        $arguments = @('-Dstarfall.haijiaVisualCheck=true') + $arguments + @('--width', '1440', '--height', '1000')
    }
    if ($BestCockpitCheck) {
        $arguments = @('-Dstarfall.bestCockpitCheck=true') + $arguments + @('--width', '1440', '--height', '1000')
    }
    if ($BestMechVisualCheck) {
        $arguments = @('-Dstarfall.visualCheck=true', '-Dstarfall.bestMechVisualCheck=true') + $arguments + @('--width', '1440', '--height', '1000')
    }
    if ($HaijiaFlightCheck) {
        $arguments = @('-Dstarfall.haijiaFlightCheck=true') + $arguments + @('--width', '1440', '--height', '1000')
    }
    if ($EsperVisualCheck) {
        $arguments = @('-Dstarfall.esperVisualCheck=true') + $arguments + @('--width', '1440', '--height', '1000')
    }
    $launchWindowStyle = if ($ShowWindow) { 'Normal' } else { 'Hidden' }
    $process = Start-Process -FilePath $JavaExecutable -ArgumentList $arguments -WorkingDirectory $gameDirectoryPath -WindowStyle $launchWindowStyle `
        -RedirectStandardOutput (Join-Path $gameDirectoryPath 'prepared-client.log') `
        -RedirectStandardError (Join-Path $gameDirectoryPath 'prepared-client-error.log') -PassThru
    Write-Output "Started Minecraft client PID $($process.Id) in $gameDirectoryPath"
} finally { $env:MOD_CLASSES = $previousModClasses }
