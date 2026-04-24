@echo off
setlocal EnableExtensions

set "REDIS_URL=%REDIS_URL%"
if not defined REDIS_URL set "REDIS_URL=%REDIS_MCP_URL%"
if not defined REDIS_URL set "REDIS_URL=%1"

if not defined REDIS_URL (
  echo Missing REDIS_URL. >&2
  exit /b 1
)

where uvx >nul 2>&1
if %errorlevel%==0 (
  uvx --from redis-mcp-server@latest redis-mcp-server --url "%REDIS_URL%"
  exit /b %errorlevel%
)

set "UVX_FALLBACK=%USERPROFILE%\.local\bin\uvx.exe"
if exist "%UVX_FALLBACK%" (
  "%UVX_FALLBACK%" --from redis-mcp-server@latest redis-mcp-server --url "%REDIS_URL%"
  exit /b %errorlevel%
)

echo uvx not found on PATH. Install uv (or add %USERPROFILE%\.local\bin to PATH) and retry. >&2
exit /b 1
