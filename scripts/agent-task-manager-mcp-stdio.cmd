@echo off
setlocal EnableExtensions

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..") do set "REPO_ROOT=%%~fI"
set "JAR_PATH=%REPO_ROOT%\agent-task-manager-app\target\agent-task-manager-app-0.1.0-SNAPSHOT.jar"

if not exist "%JAR_PATH%" (
  call mvn -q -pl agent-task-manager-app -am package
  if errorlevel 1 exit /b %errorlevel%
)

java -jar "%JAR_PATH%" serve-mcp-stdio %*
