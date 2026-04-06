param(
    [string]$JavaExe = $(if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }),
    [string]$Password = 'local-dev-password',
    [int]$Port = 9000,
    [bool]$EnableCodexClientPlatform = $true,
    [bool]$ProxyAuthEnabled = $false,
    [bool]$AutonomyEnabled = $false,
    [string]$SpringLogLevel = 'WARN',
    [string]$AppLogLevel = 'INFO'
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$jarPath = Join-Path $repoRoot 'tavall-ai-app\target\tavall-ai-app-0.1.0-SNAPSHOT.jar'
$logDir = Join-Path $repoRoot ".tmp\local-backend\$Port"
$stdoutPath = Join-Path $logDir 'stdout.log'
$stderrPath = Join-Path $logDir 'stderr.log'

if (-not (Test-Path $jarPath)) {
    throw "Jar not found at '$jarPath'. Build it first with: mvn -pl tavall-ai-app -am -DskipTests package"
}

New-Item -ItemType Directory -Force -Path $logDir | Out-Null
if (Test-Path $stdoutPath) { Remove-Item $stdoutPath -Force }
if (Test-Path $stderrPath) { Remove-Item $stderrPath -Force }

$arguments = @(
    '-jar',
    $jarPath,
    "--server.port=$Port",
    "--app.security.password=$Password",
    "--app.codex-client-platform.enabled=$($EnableCodexClientPlatform.ToString().ToLowerInvariant())",
    "--app.security.proxy-auth-enabled=$($ProxyAuthEnabled.ToString().ToLowerInvariant())",
    "--app.orchestration.autonomy-enabled=$($AutonomyEnabled.ToString().ToLowerInvariant())",
    "--logging.level.org.springframework=$SpringLogLevel",
    "--logging.level.org.tavall.ai=$AppLogLevel"
)

$process = Start-Process `
    -FilePath $JavaExe `
    -WorkingDirectory $repoRoot `
    -ArgumentList $arguments `
    -RedirectStandardOutput $stdoutPath `
    -RedirectStandardError $stderrPath `
    -PassThru

[pscustomobject]@{
    ProcessId = $process.Id
    Port = $Port
    JarPath = $jarPath
    StdoutLog = $stdoutPath
    StderrLog = $stderrPath
}


