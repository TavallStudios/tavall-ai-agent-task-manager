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
  uvx redis-mcp-server --url "%REDIS_URL%"
  exit /b %errorlevel%
)

where redis-mcp-server >nul 2>&1
if %errorlevel%==0 (
  redis-mcp-server --url "%REDIS_URL%"
  exit /b %errorlevel%
)

echo redis-mcp-server not found. Install via 'uvx redis-mcp-server' or ensure it is on PATH. >&2
exit /b 1
