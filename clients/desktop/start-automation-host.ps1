param(
    [switch]$Build,
    [switch]$Http,
    [int]$HttpPort = 54123,
    [string]$HttpPrefix,
    [string]$AuthToken = $env:AGENT_TASK_MANAGER_COMPUTER_USE_RUNNER_AUTH_TOKEN,
    [int]$LeaseTtlSeconds = 60,
    [string]$ServiceVersion = "1.0"
)

$ErrorActionPreference = "Stop"

$dotnet = "C:\Program Files\dotnet\dotnet.exe"
if (-not (Test-Path $dotnet)) {
    throw "The x64 dotnet SDK host was not found at $dotnet"
}

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectPath = Join-Path $projectRoot "AgentTaskManager.AutomationHost\AgentTaskManager.AutomationHost.csproj"
$dllPath = Join-Path $projectRoot "AgentTaskManager.AutomationHost\bin\x64\Debug\net8.0-windows10.0.19041.0\AgentTaskManager.AutomationHost.dll"

if ($Build) {
    & $dotnet build $projectPath -c Debug -p:Platform=x64
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}

if (-not (Test-Path $dllPath)) {
    throw "Automation host binary not found. Run with -Build first."
}

$hostArgs = @()
if ($HttpPrefix) {
    $hostArgs += "--http-prefix"
    $hostArgs += $HttpPrefix
}
elseif ($Http) {
    $hostArgs += "--http-port"
    $hostArgs += "$HttpPort"
}

if (-not [string]::IsNullOrWhiteSpace($AuthToken)) {
    $hostArgs += "--auth-token"
    $hostArgs += $AuthToken
}

if ($LeaseTtlSeconds -gt 0) {
    $hostArgs += "--lease-ttl-seconds"
    $hostArgs += "$LeaseTtlSeconds"
}

if (-not [string]::IsNullOrWhiteSpace($ServiceVersion)) {
    $hostArgs += "--service-version"
    $hostArgs += $ServiceVersion
}

& $dotnet $dllPath @hostArgs
