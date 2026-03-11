param(
    [string]$ConfigPath = "$PSScriptRoot\agent-task-manager-bridge.config.json",
    [string]$TaskName = "AgentTaskManagerLocalBridge"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $ConfigPath)) {
    throw "Missing config file: $ConfigPath"
}

$runner = Join-Path $PSScriptRoot "start-local-bridge.ps1"
if (-not (Test-Path $runner)) {
    throw "Missing runner script: $runner"
}

$action = New-ScheduledTaskAction `
    -Execute "powershell.exe" `
    -Argument "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$runner`" -ConfigPath `"$ConfigPath`""

$trigger = New-ScheduledTaskTrigger -AtLogOn
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries

Register-ScheduledTask `
    -TaskName $TaskName `
    -Action $action `
    -Trigger $trigger `
    -Settings $settings `
    -Description "Start the AgentTaskManager local IDE bridge at logon" `
    -Force | Out-Null

Write-Host "Registered scheduled task '$TaskName'."
Write-Host "Use the config file at: $ConfigPath"
