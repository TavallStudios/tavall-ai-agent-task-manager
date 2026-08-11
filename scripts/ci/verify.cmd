@echo off
setlocal

cd /d "%~dp0\..\.."
if "%TAVALL_CI_MAX_WORKERS%"=="" set TAVALL_CI_MAX_WORKERS=2

call gradlew.bat --no-daemon --max-workers=%TAVALL_CI_MAX_WORKERS% clean check
exit /b %ERRORLEVEL%
