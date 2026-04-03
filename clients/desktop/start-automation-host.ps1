param(
    [switch]$Build,
    [switch]$Http,
    [int]$HttpPort = 54123,
    [string]$HttpPrefix
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

& $dotnet $dllPath @hostArgs
