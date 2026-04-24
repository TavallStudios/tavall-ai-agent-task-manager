param(
    [string]$DisplayDeviceName = "DISPLAY2",
    [int]$Margin = 80,
    [int]$LauncherTimeoutMs = 30000,
    [int]$ClientTimeoutMs = 120000,
    [int]$MaxAttempts = 2
)

$ErrorActionPreference = "Stop"

$dotnet = "C:\Program Files\dotnet\dotnet.exe"
$desktopRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$solutionPath = Join-Path $desktopRoot "AgentTaskManager.Desktop.sln"
$hostDll = Join-Path $desktopRoot "AgentTaskManager.AutomationHost\bin\x64\Debug\net8.0-windows10.0.19041.0\AgentTaskManager.AutomationHost.dll"
$launcherPath = "F:\Hytale Launcher\hytale-launcher.exe"
$artifactDir = "F:\workspace\_codex_temp"

function Invoke-AutomationRequest {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable]$Body
    )

    $json = $Body | ConvertTo-Json -Compress -Depth 10
    $response = $json | & $dotnet $hostDll
    if ($LASTEXITCODE -ne 0) {
        throw "Automation host failed while executing request: $json`nHost response: $response"
    }

    $parsed = ([string]$response).TrimStart([char]0xFEFF) | ConvertFrom-Json
    if (-not $parsed.ok) {
        throw "Automation request failed: $($parsed.error.message)"
    }

    return $parsed.result
}

function Wait-Window {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable]$Window,
        [int]$TimeoutMs = 10000
    )

    return Invoke-AutomationRequest @{
        id = "wait-window"
        command = "wait_for_window"
        parameters = @{
            window = $Window
            timeoutMs = $TimeoutMs
            includeInvisible = $true
        }
    }
}

function Move-Window {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable]$Window,
        [Parameter(Mandatory = $true)]
        [int]$Left,
        [Parameter(Mandatory = $true)]
        [int]$Top,
        [Parameter(Mandatory = $true)]
        [int]$Width,
        [Parameter(Mandatory = $true)]
        [int]$Height
    )

    return Invoke-AutomationRequest @{
        id = "move-window"
        command = "move_window"
        parameters = @{
            window = $Window
            left = $Left
            top = $Top
            width = $Width
            height = $Height
            restoreIfMinimized = $true
            activateWindow = $false
        }
    }
}

function Invoke-Element {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable]$Window,
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [string]$ControlType = "Button"
    )

    return Invoke-AutomationRequest @{
        id = "invoke-element"
        command = "invoke_element"
        parameters = @{
            window = $Window
            selector = @{
                name = $Name
                controlType = $ControlType
            }
        }
    }
}

function Find-Elements {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable]$Window,
        [Parameter(Mandatory = $true)]
        [hashtable]$Selector,
        [int]$MaxDepth = 8,
        [int]$MaxResults = 5
    )

    return Invoke-AutomationRequest @{
        id = "find-elements"
        command = "find_elements"
        parameters = @{
            window = $Window
            selector = $Selector
            maxDepth = $MaxDepth
            maxResults = $MaxResults
        }
    }
}

function Capture-Window {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable]$Window,
        [Parameter(Mandatory = $true)]
        [string]$OutputPath
    )

    return Invoke-AutomationRequest @{
        id = "capture-window"
        command = "capture_window"
        parameters = @{
            window = $Window
            outputPath = $OutputPath
            allowScreenCopyFallback = $true
        }
    }
}

function Get-DisplayTarget {
    Add-Type -AssemblyName System.Windows.Forms
    $screen = [System.Windows.Forms.Screen]::AllScreens |
        Where-Object { $_.DeviceName -like "*$DisplayDeviceName*" } |
        Select-Object -First 1
    if ($null -eq $screen) {
        throw "Display '$DisplayDeviceName' was not found."
    }

    return $screen.WorkingArea
}

function Wait-Element {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable]$Window,
        [Parameter(Mandatory = $true)]
        [hashtable]$Selector,
        [int]$TimeoutMs = 30000,
        [int]$PollIntervalMs = 1000
    )

    $deadline = [DateTimeOffset]::UtcNow.AddMilliseconds($TimeoutMs)
    do {
        try {
            $matches = @(Find-Elements -Window $Window -Selector $Selector -MaxResults 1)
            if ($matches.Count -gt 0) {
                return $matches[0]
            }
        }
        catch {
        }

        Start-Sleep -Milliseconds $PollIntervalMs
    } while ([DateTimeOffset]::UtcNow -lt $deadline)

    throw "Timed out waiting for a matching element."
}

function Stop-AuthErrorClients {
    $stopped = @()
    Get-Process HytaleClient -ErrorAction SilentlyContinue |
        Where-Object { $_.MainWindowTitle -eq "Authentication Error" } |
        ForEach-Object {
            $stopped += $_.Id
            Stop-Process -Id $_.Id -Force -ErrorAction Stop
        }
    return $stopped
}

if (!(Test-Path $artifactDir)) {
    New-Item -ItemType Directory -Path $artifactDir | Out-Null
}

& $dotnet build $solutionPath -c Debug -p:Platform=x64
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

$targetDisplay = Get-DisplayTarget
$launcherWindowTarget = @{ processName = "hytale-launcher" }
$clientWindowTarget = @{ processName = "HytaleClient" }

if (-not (Get-Process hytale-launcher -ErrorAction SilentlyContinue)) {
    if (!(Test-Path $launcherPath)) {
        throw "Hytale launcher was not found at $launcherPath"
    }

    Start-Process -FilePath $launcherPath | Out-Null
}

$launcherWindow = Wait-Window -Window $launcherWindowTarget -TimeoutMs $LauncherTimeoutMs
$launcherWidth = [Math]::Min(1440, [Math]::Max(900, $targetDisplay.Width - ($Margin * 2)))
$launcherHeight = [Math]::Min(980, [Math]::Max(700, $targetDisplay.Height - ($Margin * 2)))
$launcherLeft = $targetDisplay.X + $Margin
$launcherTop = $targetDisplay.Y + $Margin
$launcherMove = Move-Window -Window $launcherWindowTarget -Left $launcherLeft -Top $launcherTop -Width $launcherWidth -Height $launcherHeight
$launcherCapture = Capture-Window -Window $launcherWindowTarget -OutputPath (Join-Path $artifactDir "hytale-launcher-smoke.png")
Wait-Element -Window $launcherWindowTarget -Selector @{ name = "PLAY"; controlType = "Button" } -TimeoutMs $LauncherTimeoutMs | Out-Null

$attemptHistory = @()
$clientWindow = $null
for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
    $stoppedAuthErrors = Stop-AuthErrorClients
    $invoke = Invoke-Element -Window $launcherWindowTarget -Name "PLAY"
    Start-Sleep -Seconds 5
    try {
        $candidate = Wait-Window -Window $clientWindowTarget -TimeoutMs $ClientTimeoutMs
        $attemptHistory += [PSCustomObject]@{
            Attempt = $attempt
            ClientTitle = $candidate.title
            StoppedAuthErrors = ($stoppedAuthErrors -join ",")
        }

        if ($candidate.title -eq "Authentication Error") {
            Stop-AuthErrorClients | Out-Null
            continue
        }

        $clientWindow = $candidate
        break
    }
    catch {
        $attemptHistory += [PSCustomObject]@{
            Attempt = $attempt
            ClientTitle = "launch-timeout"
            StoppedAuthErrors = ($stoppedAuthErrors -join ",")
        }
    }
}

if ($null -eq $clientWindow) {
    throw "Hytale client did not reach a usable window after $MaxAttempts attempts."
}

$clientWidth = [Math]::Min(1600, [Math]::Max(1100, $targetDisplay.Width - ($Margin * 2)))
$clientHeight = [Math]::Min(980, [Math]::Max(720, $targetDisplay.Height - ($Margin * 2)))
$clientLeft = $targetDisplay.X + [Math]::Max($Margin, [Math]::Floor(($targetDisplay.Width - $clientWidth) / 2))
$clientTop = $targetDisplay.Y + [Math]::Max($Margin, [Math]::Floor(($targetDisplay.Height - $clientHeight) / 2))
$clientMove = Move-Window -Window $clientWindowTarget -Left $clientLeft -Top $clientTop -Width $clientWidth -Height $clientHeight
$clientCapture = Capture-Window -Window $clientWindowTarget -OutputPath (Join-Path $artifactDir "hytale-client-smoke.png")

[PSCustomObject]@{
    Display = $targetDisplay
    LauncherWindow = $launcherWindow
    LauncherMove = $launcherMove
    LauncherCapture = $launcherCapture.outputPath
    ClientWindow = $clientWindow
    ClientMove = $clientMove
    ClientCapture = $clientCapture.outputPath
    AttemptHistory = $attemptHistory
} | Format-List
