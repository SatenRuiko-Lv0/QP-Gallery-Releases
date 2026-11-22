param(
    [string] $JavaDir = $(if ($env:JAVA_HOME) { $env:JAVA_HOME } else { 'D:\Program Files\Android\Android Studio\jbr' }),
    [string] $AndroidSdk = "$env:LOCALAPPDATA\Android\Sdk",
    [string] $OutputApk = "$PSScriptRoot\..\dist\QP-Gallery-LiquidGlass-v4.apk",
    [switch] $Offline
)

$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = $JavaDir
$env:ANDROID_HOME = $AndroidSdk

$GradleArgs = @('-p', $PSScriptRoot, ':app:assembleRelease', '--console=plain')
if ($Offline) { $GradleArgs += '--offline' }

& (Join-Path $PSScriptRoot 'gradlew.bat') @GradleArgs
if ($LASTEXITCODE -ne 0) { throw "Gradle build failed with exit code $LASTEXITCODE" }

$BuiltApk = Join-Path $PSScriptRoot 'app\build\outputs\apk\release\app-release.apk'
$ResolvedOut = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputApk)
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $ResolvedOut) | Out-Null
Copy-Item -LiteralPath $BuiltApk -Destination $ResolvedOut -Force
Write-Output $ResolvedOut
