@echo off
setlocal EnableExtensions

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..") do set "REPO_ROOT=%%~fI"
for /f "usebackq delims=" %%P in (`powershell -NoProfile -Command "Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'java.exe' -and $_.CommandLine -match 'AgentTaskManagerLauncher' } | Select-Object -First 1 -ExpandProperty ProcessId"`) do set "EXISTING_PID=%%P"

if defined EXISTING_PID (
  echo tavall-ai MCP stdio already running (pid=%EXISTING_PID%).
  exit /b 1
)

set "DISTRIBUTION_PATH=%REPO_ROOT%\distribution\agent-task-manager"
if not exist "%DISTRIBUTION_PATH%\application.jar" (
  call "%REPO_ROOT%\gradlew.bat" --no-daemon --max-workers=1 stageDistribution
  if errorlevel 1 exit /b %errorlevel%
)

if not exist "%DISTRIBUTION_PATH%\application.jar" (
  echo Failed to prepare the AgentTaskManager distribution.
  exit /b 1
)

set "BRIDGE_SCRIPT=%REPO_ROOT%\scripts\mcp_stdio_json_bridge.py"
set "BRIDGE_ARGS=--cwd \"%REPO_ROOT%\" --distribution-path \"%DISTRIBUTION_PATH%\""

if defined TAVALL_AI_STDIO_PROTOCOL (
  if not "%TAVALL_AI_STDIO_PROTOCOL%"=="" set "BRIDGE_ARGS=%BRIDGE_ARGS% --protocol %TAVALL_AI_STDIO_PROTOCOL%"
)

if defined TAVALL_AI_STDIO_DISABLE_DB (
  if not "%TAVALL_AI_STDIO_DISABLE_DB%"=="" set "BRIDGE_ARGS=%BRIDGE_ARGS% --disable-db"
)

python "%BRIDGE_SCRIPT%" %BRIDGE_ARGS% -- %*
