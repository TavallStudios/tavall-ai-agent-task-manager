param(
    [switch]$Build,
    [int]$HttpPort = 54123,
    [string]$HttpPrefix,
    [string]$RunnerId = "hytale-runner-1",
    [string]$HytaleLauncherPath = $env:AGENT_TASK_MANAGER_HYTALE_LAUNCHER_PATH,
    [string]$HytaleClientPath = $env:AGENT_TASK_MANAGER_HYTALE_CLIENT_PATH
)

$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$startScript = Join-Path $scriptRoot "start-automation-host.ps1"
$hostName = [System.Net.Dns]::GetHostName()

$baseUrl = if ($HttpPrefix) {
    $HttpPrefix.TrimEnd("/")
}
else {
    "http://$hostName`:$HttpPort"
}

$registrationPayload = @{
    runnerId = $RunnerId
    displayName = "Hytale Runner ($hostName)"
    hostName = $hostName
    baseUrl = $baseUrl
    launcherPath = $HytaleLauncherPath
    clientPath = $HytaleClientPath
    supportedCaptureModes = @("graphics-capture", "copyFromScreen")
    capabilities = @{
        gameSupport = $true
        captureCommands = @("capture_region", "capture_stream_frame", "match_template")
        inputCommands = @("send_key_batch", "send_mouse_batch")
    }
    metadata = @{
        profile = "hytale"
        windowedOnly = $true
    }
}

Write-Host ""
Write-Host "Suggested registerComputerUseRunner payload:"
$registrationPayload | ConvertTo-Json -Depth 6 | Write-Host
Write-Host ""
Write-Host "Suggested AgentTaskManager env overrides:"
Write-Host "  AGENT_TASK_MANAGER_HYTALE_LAUNCHER_PATH=$HytaleLauncherPath"
Write-Host "  AGENT_TASK_MANAGER_HYTALE_CLIENT_PATH=$HytaleClientPath"
Write-Host ""

$arguments = @{}
if ($Build) {
    $arguments.Build = $true
}

if ($HttpPrefix) {
    $arguments.HttpPrefix = $HttpPrefix
}
else {
    $arguments.Http = $true
    $arguments.HttpPort = $HttpPort
}

& $startScript @arguments
