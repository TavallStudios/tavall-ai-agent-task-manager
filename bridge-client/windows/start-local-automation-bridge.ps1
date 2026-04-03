param(
    [string]$ConfigPath = "$PSScriptRoot\agent-task-manager-automation-bridge.config.json"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $ConfigPath)) {
    throw "Missing config file: $ConfigPath"
}

$config = Get-Content -Raw -Path $ConfigPath | ConvertFrom-Json
$scriptRoot = Split-Path -Parent $PSScriptRoot
$pythonScript = Join-Path $scriptRoot "agent_task_manager_local_automation_bridge.py"

if (-not (Test-Path $pythonScript)) {
    throw "Missing bridge script: $pythonScript"
}

$pythonCommand = if ($config.pythonCommand) { [string]$config.pythonCommand } else { "py -3" }
$pythonParts = $pythonCommand -split '\s+'
$pythonExe = $pythonParts[0]
$pythonArgs = @()
if ($pythonParts.Length -gt 1) {
    $pythonArgs = $pythonParts[1..($pythonParts.Length - 1)]
}

$arguments = @()
$arguments += $pythonArgs
$arguments += $pythonScript
$arguments += "--base-url"
$arguments += [string]$config.baseUrl
$arguments += "--username"
$arguments += [string]$config.username
$arguments += "--password"
$arguments += [string]$config.password
$arguments += "--provider-url"
$arguments += [string]$config.providerUrl
$arguments += "--bridge-target"
$arguments += [string]$config.bridgeTarget
$arguments += "--agent-id"
$arguments += [string]$config.agentId
$arguments += "--client-name"
$arguments += [string]$config.clientName
$arguments += "--poll-interval"
$arguments += [string]$config.pollInterval
$arguments += "--session-file"
$arguments += [string]$config.sessionFile

if ($config.repoPath) {
    $arguments += "--repo-path"
    $arguments += [string]$config.repoPath
}

if ($config.commands) {
    foreach ($commandId in $config.commands) {
        $arguments += "--command"
        $arguments += [string]$commandId
    }
}

Write-Host "Starting AgentTaskManager local automation bridge from $pythonScript"
Write-Host "Base URL: $($config.baseUrl)"
Write-Host "Provider URL: $($config.providerUrl)"

& $pythonExe @arguments
