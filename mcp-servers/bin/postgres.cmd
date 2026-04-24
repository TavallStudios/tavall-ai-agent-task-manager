@echo off
setlocal EnableExtensions

set "POSTGRES_URL=%POSTGRES_URL%"
if not defined POSTGRES_URL set "POSTGRES_URL=%DATABASE_URL%"
if not defined POSTGRES_URL set "POSTGRES_URL=%PG_URL%"
if not defined POSTGRES_URL set "POSTGRES_URL=%1"

if not defined POSTGRES_URL (
  echo Missing POSTGRES_URL or DATABASE_URL. >&2
  exit /b 1
)

npx -y @modelcontextprotocol/server-postgres "%POSTGRES_URL%"
