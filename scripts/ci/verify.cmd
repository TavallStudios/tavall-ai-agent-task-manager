@echo off
setlocal

cd /d "%~dp0\..\.."
if "%TAVALL_CI_MAX_WORKERS%"=="" set TAVALL_CI_MAX_WORKERS=2

rem Canonical Tavall AI verification entrypoint for Windows/local execution surfaces.
call gradlew.bat --no-daemon --max-workers=%TAVALL_CI_MAX_WORKERS% clean check stageDistribution
exit /b %ERRORLEVEL%
