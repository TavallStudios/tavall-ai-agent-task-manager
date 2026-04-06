@echo off
setlocal EnableExtensions

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..") do set "REPO_ROOT=%%~fI"
for /f "usebackq delims=" %%P in (`powershell -NoProfile -Command "Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'java.exe' -and $_.CommandLine -match 'tavall-ai-app' } | Select-Object -First 1 -ExpandProperty ProcessId"`) do set "EXISTING_PID=%%P"

if defined EXISTING_PID (
  echo tavall-ai MCP stdio already running (pid=%EXISTING_PID%).
  exit /b 1
)

set "TARGET_DIR=%REPO_ROOT%\tavall-ai-app\target"
for /f "usebackq delims=" %%J in (`dir /b /o-d "%TARGET_DIR%\tavall-ai-app-*.jar" ^| findstr /v /i "sources javadoc tests plain"`) do (
  set "JAR_PATH=%TARGET_DIR%\%%J"
  goto :jar_found
)

:jar_found
if not defined JAR_PATH (
  if exist "%REPO_ROOT%\mvnw.cmd" (
    call "%REPO_ROOT%\mvnw.cmd" -q -pl tavall-ai-app -am "-Dmaven.test.skip=true" package
  ) else (
    call mvn -q -pl tavall-ai-app -am "-Dmaven.test.skip=true" package
  )
  if errorlevel 1 exit /b %errorlevel%
  for /f "usebackq delims=" %%J in (`dir /b /o-d "%TARGET_DIR%\tavall-ai-app-*.jar" ^| findstr /v /i "sources javadoc tests plain"`) do (
    set "JAR_PATH=%TARGET_DIR%\%%J"
    goto :jar_found_done
  )
) else (
  goto :jar_found_done
)

:jar_found_done
if not defined JAR_PATH (
  echo Failed to locate tavall-ai-app jar in %TARGET_DIR%.
  exit /b 1
)

java --enable-preview -jar "%JAR_PATH%" serve-mcp-stdio %*

