param(
    [string]$DisplayDeviceName = "DISPLAY2",
    [int]$Margin = 80,
    [int]$WindowTimeoutMs = 30000,
    [string]$ArtifactDir = "F:\workspace\_codex_temp",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

$dotnet = "C:\Program Files\dotnet\dotnet.exe"
$desktopRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$solutionPath = Join-Path $desktopRoot "AgentTaskManager.Desktop.sln"
$hostDll = Join-Path $desktopRoot "AgentTaskManager.AutomationHost\bin\x64\Debug\net8.0-windows10.0.19041.0\AgentTaskManager.AutomationHost.dll"
$clientWindowTarget = @{ processName = "HytaleClient"; titleContains = "Hytale" }
$editorWindowTarget = @{ titleContains = "Hytale Asset Editor"; processName = "HytaleClient" }
$clientLogRoot = "F:\Games\Hytale\UserData\Logs"

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
Add-Type @'
using System;
using System.Runtime.InteropServices;

public static class HytaleWindowGeometry {
    [StructLayout(LayoutKind.Sequential)]
    public struct RECT {
        public int Left;
        public int Top;
        public int Right;
        public int Bottom;
    }

    [StructLayout(LayoutKind.Sequential)]
    public struct POINT {
        public int X;
        public int Y;
    }

    [DllImport("user32.dll")]
    public static extern bool GetWindowRect(IntPtr hWnd, out RECT rect);

    [DllImport("user32.dll")]
    public static extern bool ClientToScreen(IntPtr hWnd, ref POINT point);

    [DllImport("user32.dll")]
    public static extern IntPtr GetForegroundWindow();

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    public static extern int GetWindowText(IntPtr hWnd, System.Text.StringBuilder text, int count);
}
'@

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

function Try-WaitWindow {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable]$Window,
        [int]$TimeoutMs = 4000
    )

    try {
        return Wait-Window -Window $Window -TimeoutMs $TimeoutMs
    }
    catch {
        return $null
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

function Capture-Window {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable]$Window,
        [Parameter(Mandatory = $true)]
        [string]$OutputPath
    )

    try {
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
    catch {
        if (-not $_.Exception.Message.Contains("GDI+")) {
            throw
        }

        return Capture-StreamFrame -Window $Window
    }
}

function Capture-StreamFrame {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable]$Window
    )

    return Invoke-AutomationRequest @{
        id = "capture-stream-frame"
        command = "capture_stream_frame"
        parameters = @{
            window = $Window
            allowScreenCopyFallback = $true
            includeBase64 = $false
        }
    }
}

function Click-Point {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable]$Window,
        [Parameter(Mandatory = $true)]
        [int]$X,
        [Parameter(Mandatory = $true)]
        [int]$Y
    )

    return Invoke-AutomationRequest @{
        id = "click-point"
        command = "click_point"
        parameters = @{
            window = $Window
            x = $X
            y = $Y
            mode = "windowMessage"
            activateWindow = $false
        }
    }
}

function Get-DisplayTarget {
    $screen = [System.Windows.Forms.Screen]::AllScreens |
        Where-Object { $_.DeviceName -like "*$DisplayDeviceName*" } |
        Select-Object -First 1
    if ($null -eq $screen) {
        throw "Display '$DisplayDeviceName' was not found."
    }

    return $screen.WorkingArea
}

function Get-ForegroundWindowTitle {
    $handle = [HytaleWindowGeometry]::GetForegroundWindow()
    $builder = New-Object System.Text.StringBuilder 512
    [void][HytaleWindowGeometry]::GetWindowText($handle, $builder, $builder.Capacity)
    return "{0}:{1}" -f $handle.ToInt64().ToString("X"), $builder.ToString()
}

function Convert-ScreenshotPointToClientPoint {
    param(
        [Parameter(Mandatory = $true)]
        [long]$Handle,
        [Parameter(Mandatory = $true)]
        [int]$ScreenshotX,
        [Parameter(Mandatory = $true)]
        [int]$ScreenshotY
    )

    $windowRect = New-Object HytaleWindowGeometry+RECT
    $clientOrigin = New-Object HytaleWindowGeometry+POINT
    if (-not [HytaleWindowGeometry]::GetWindowRect([IntPtr]$Handle, [ref]$windowRect)) {
        throw "Failed to resolve the Hytale window bounds."
    }
    if (-not [HytaleWindowGeometry]::ClientToScreen([IntPtr]$Handle, [ref]$clientOrigin)) {
        throw "Failed to resolve the Hytale client origin."
    }

    [PSCustomObject]@{
        X = $ScreenshotX - ($clientOrigin.X - $windowRect.Left)
        Y = $ScreenshotY - ($clientOrigin.Y - $windowRect.Top)
    }
}

function Test-CreativeToolsOverlayVisible {
    param(
        [Parameter(Mandatory = $true)]
        [string]$CapturePath
    )

    $bitmap = [System.Drawing.Bitmap]::FromFile($CapturePath)
    try {
        $pixel = $bitmap.GetPixel(260, 86)
        return ($pixel.R + $pixel.G + $pixel.B) -lt 150
    }
    finally {
        $bitmap.Dispose()
    }
}

function Start-AssetEditorFromLatestClientLog {
    if (-not (Test-Path -LiteralPath $clientLogRoot -PathType Container)) {
        throw "Client log directory not found: $clientLogRoot"
    }

    $launchLine = Get-ChildItem -LiteralPath $clientLogRoot -Filter "*_client.log" |
        Sort-Object LastWriteTimeUtc -Descending |
        ForEach-Object {
            Select-String -Path $_.FullName -Pattern 'Launching asset editor:\s+(?<exe>[A-Z]:\\.+?\.exe)\s+(?<args>--editor.+)$' |
                Select-Object -Last 1
        } |
        Where-Object { $_ } |
        Select-Object -First 1

    if ($null -eq $launchLine) {
        throw "No authenticated asset editor launch command was found in the client logs."
    }

    $match = [regex]::Match($launchLine.Line, 'Launching asset editor:\s+(?<exe>[A-Z]:\\.+?\.exe)\s+(?<args>--editor.+)$')
    if (-not $match.Success) {
        throw "Failed to parse the asset editor launch command from the client log."
    }

    Start-Process -FilePath $match.Groups["exe"].Value -ArgumentList $match.Groups["args"].Value -WorkingDirectory (Split-Path -Parent $match.Groups["exe"].Value) | Out-Null
}

if (!(Test-Path -LiteralPath $ArtifactDir)) {
    New-Item -ItemType Directory -Path $ArtifactDir | Out-Null
}

if (-not $SkipBuild) {
    & $dotnet build $solutionPath -c Debug -p:Platform=x64
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}

$foregroundBefore = Get-ForegroundWindowTitle
$targetDisplay = Get-DisplayTarget
$clientWindow = Wait-Window -Window $clientWindowTarget -TimeoutMs $WindowTimeoutMs
$clientWidth = [Math]::Min(1600, [Math]::Max(1100, $targetDisplay.Width - ($Margin * 2)))
$clientHeight = [Math]::Min(980, [Math]::Max(720, $targetDisplay.Height - ($Margin * 2)))
$clientLeft = $targetDisplay.X + [Math]::Max($Margin, [Math]::Floor(($targetDisplay.Width - $clientWidth) / 2))
$clientTop = $targetDisplay.Y + [Math]::Max($Margin, [Math]::Floor(($targetDisplay.Height - $clientHeight) / 2))
$clientMove = Move-Window -Window $clientWindowTarget -Left $clientLeft -Top $clientTop -Width $clientWidth -Height $clientHeight
$clientCapturePath = Join-Path $ArtifactDir "hytale-creative-client.png"
$clientCapture = Capture-Window -Window $clientWindowTarget -OutputPath $clientCapturePath

$editorWindow = Try-WaitWindow -Window $editorWindowTarget
$launchPath = "existing-editor"
if ($null -eq $editorWindow) {
    if (Test-CreativeToolsOverlayVisible -CapturePath $clientCapturePath) {
        $assetsMenuPoint = Convert-ScreenshotPointToClientPoint -Handle $clientWindow.handle -ScreenshotX 135 -ScreenshotY 96
        $assetEditorPoint = Convert-ScreenshotPointToClientPoint -Handle $clientWindow.handle -ScreenshotX 135 -ScreenshotY 136
        Click-Point -Window $clientWindowTarget -X $assetsMenuPoint.X -Y $assetsMenuPoint.Y | Out-Null
        Start-Sleep -Milliseconds 500
        Click-Point -Window $clientWindowTarget -X $assetEditorPoint.X -Y $assetEditorPoint.Y | Out-Null
        Start-Sleep -Seconds 2
        $editorWindow = Try-WaitWindow -Window $editorWindowTarget -TimeoutMs 6000
        $launchPath = "creative-tools-click"
    }

    if ($null -eq $editorWindow) {
        Start-AssetEditorFromLatestClientLog
        $editorWindow = Wait-Window -Window $editorWindowTarget -TimeoutMs $WindowTimeoutMs
        $launchPath = "client-log-fallback"
    }
}

$editorWidth = [Math]::Min(1296, [Math]::Max(1000, $targetDisplay.Width - ($Margin * 2)))
$editorHeight = [Math]::Min(820, [Math]::Max(700, $targetDisplay.Height - ($Margin * 2)))
$editorLeft = $targetDisplay.X + [Math]::Max($Margin, [Math]::Floor(($targetDisplay.Width - $editorWidth) / 2))
$editorTop = $targetDisplay.Y + [Math]::Max($Margin, [Math]::Floor(($targetDisplay.Height - $editorHeight) / 2))
$editorMove = Move-Window -Window $editorWindowTarget -Left $editorLeft -Top $editorTop -Width $editorWidth -Height $editorHeight
$editorCapture = Capture-StreamFrame -Window $editorWindowTarget
$foregroundAfter = Get-ForegroundWindowTitle

[PSCustomObject]@{
    Display = $targetDisplay
    ForegroundBefore = $foregroundBefore
    ForegroundAfter = $foregroundAfter
    ForegroundPreserved = ($foregroundBefore -eq $foregroundAfter)
    ClientWindow = $clientWindow
    ClientMove = $clientMove
    ClientCapture = $clientCapture.outputPath
    CreativeOverlayVisible = Test-CreativeToolsOverlayVisible -CapturePath $clientCapturePath
    AssetEditorLaunchPath = $launchPath
    AssetEditorWindow = $editorWindow
    AssetEditorMove = $editorMove
    AssetEditorCapture = $editorCapture.outputPath
    AssetEditorCaptureMode = $editorCapture.captureMode
} | Format-List
