param()

$ErrorActionPreference = "Stop"

$dotnet = "C:\Program Files\dotnet\dotnet.exe"
$desktopRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$solutionPath = Join-Path $desktopRoot "AgentTaskManager.Desktop.sln"
$desktopExe = Join-Path $desktopRoot "AgentTaskManager.Desktop\bin\x64\Debug\net8.0-windows10.0.19041.0\win-x64\AgentTaskManager.Desktop.exe"
$hostDll = Join-Path $desktopRoot "AgentTaskManager.AutomationHost\bin\x64\Debug\net8.0-windows10.0.19041.0\AgentTaskManager.AutomationHost.dll"
$capturePath = Join-Path $desktopRoot "automation-smoke.png"

function Invoke-AutomationRequest {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Json
    )

    $response = $Json | & $dotnet $hostDll
    if ($LASTEXITCODE -ne 0) {
        throw "Automation host failed while executing request: $Json`nHost response: $response"
    }

    $normalizedResponse = ([string]$response).TrimStart([char]0xFEFF)
    $parsed = $normalizedResponse | ConvertFrom-Json
    if (-not $parsed.ok) {
        throw "Automation request failed: $($parsed.error.message)"
    }

    return $parsed.result
}

& $dotnet build $solutionPath -c Debug -p:Platform=x64
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

$process = Start-Process -FilePath $desktopExe -PassThru
try {
    Start-Sleep -Seconds 6

    $window = Invoke-AutomationRequest '{"id":"wait","command":"wait_for_window","parameters":{"window":{"processName":"AgentTaskManager.Desktop"},"timeoutMs":15000,"includeInvisible":true}}'
    $findButton = Invoke-AutomationRequest '{"id":"find","command":"find_elements","parameters":{"window":{"processName":"AgentTaskManager.Desktop"},"selector":{"automationId":"Button_SignIn"},"maxDepth":8,"maxResults":5}}'
    $setValue = Invoke-AutomationRequest '{"id":"set","command":"set_value","parameters":{"window":{"processName":"AgentTaskManager.Desktop"},"selector":{"automationId":"Field_User_name"},"value":"automation-smoke"}}'
    $capture = Invoke-AutomationRequest ("{""id"":""capture"",""command"":""capture_window"",""parameters"":{""window"":{""processName"":""AgentTaskManager.Desktop""},""outputPath"":""" + ($capturePath -replace "\\", "\\\\") + """}}")

    [PSCustomObject]@{
        WindowHandle = $window.handleHex
        SignInMatchCount = @($findButton).Count
        UserNameField = $setValue.automationId
        CapturePath = $capture.outputPath
    } | Format-Table -AutoSize
}
finally {
    if ($process -and -not $process.HasExited) {
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
    }
}
