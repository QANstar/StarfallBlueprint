param([string]$JavaExecutable = 'C:/Program Files/Java/latest/jdk-21/bin/java.exe')
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$vm=Join-Path $root 'build/moddev/gameTestServerRunVmArgs.txt'
$program=Join-Path $root 'build/moddev/gameTestServerRunProgramArgs.txt'
$cpFile=Join-Path $root 'build/moddev/gameTestServerLegacyClasspath.txt'
$jar=Join-Path $root 'build/moddev/artifacts/neoforge-21.1.228.jar'
$classes=Join-Path $root 'build/classes/java/main'
$resources=Join-Path $root 'build/resources/main'
$cp=(@($jar,$classes,$resources)+(Get-Content -LiteralPath $cpFile))-join [IO.Path]::PathSeparator
$previous=$env:MOD_CLASSES
$env:MOD_CLASSES="starfall_blueprint%%$classes$([IO.Path]::PathSeparator)starfall_blueprint%%$resources"
$run=Join-Path $root 'run/haijia-gametest'
New-Item -ItemType Directory -Force -Path $run | Out-Null
Push-Location $run
try { & $JavaExecutable -cp $cp "@$vm" '-Dneoforge.enabledGameTestNamespaces=starfall_blueprint_haijia' "@$program"; $result=$LASTEXITCODE }
finally {Pop-Location; $env:MOD_CLASSES=$previous}
exit $result
