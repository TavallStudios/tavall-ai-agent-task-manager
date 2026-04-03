param(
    [string]$OutputRoot = (Join-Path ([Environment]::GetFolderPath('MyDocuments')) 'MCP Manager Bundle')
)

$ErrorActionPreference = 'Stop'

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptRoot
$repoRoot = Split-Path -Parent $projectRoot
$binaryPath = Join-Path $repoRoot '.tmp\mcp-manager\mcp-manager.exe'

if (-not (Test-Path $binaryPath)) {
    throw "Missing built binary at $binaryPath. Build mcp-manager.exe before exporting."
}

& $binaryPath export-bundle -output-root $OutputRoot
