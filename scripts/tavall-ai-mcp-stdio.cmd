@echo off
setlocal EnableExtensions

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..") do set "REPO_ROOT=%%~fI"
set "JAR_PATH=%REPO_ROOT%\tavall-ai-app\target\tavall-ai-app-0.1.0-SNAPSHOT.jar"

if not exist "%JAR_PATH%" (
  call mvn -q -pl tavall-ai-app -am package
  if errorlevel 1 exit /b %errorlevel%
)

java -jar "%JAR_PATH%" serve-mcp-stdio %*

