@echo off
REM ============================================================
REM Load .env into the current cmd session.
REM
REM Usage:  call env.bat
REM Then the variables below are available in your current window.
REM
REM This file used to hardcode DB_PASSWORD, JWT_SECRET and a
REM DASHSCOPE_API_KEY. That key was never read by the code (the AI feature
REM uses NVIDIA, see AIConfig), and hardcoding secrets in a tracked file
REM leaks them into git history. Everything now comes from .env, which
REM .gitignore excludes.
REM
REM NOTE: deliberately no setlocal here. setlocal would scope the variables
REM to this script and lose them on exit; we want them in the caller.
REM ============================================================
setlocal enabledelayedexpansion

set "ROOT_DIR=%~dp0"
if "%ROOT_DIR:~-1%"=="\" set "ROOT_DIR=%ROOT_DIR:~0,-1%"

if not exist "%ROOT_DIR%\.env" (
    echo [env] [ERROR] .env not found. Copy .env.example to .env first.
    endlocal
    exit /b 1
)

REM Collect values locally first (needs delayed expansion), then assign them
REM in the caller's scope after endlocal using explicit expansion.
set "V_DB="
set "V_JWT="
set "V_NKEY="
set "V_NURL="
set "V_NMODEL="
set "V_RHOST="
set "V_RPORT="
set "V_RPASS="

for /f "usebackq eol=# tokens=1,* delims==" %%a in ("%ROOT_DIR%\.env") do (
    set "key=%%a"
    set "val=%%b"
    for /f "tokens=* delims= " %%k in ("!key!") do set "key=%%k"
    if /i "!key!"=="DB_PASSWORD"        set "V_DB=!val!"
    if /i "!key!"=="JWT_SECRET"         set "V_JWT=!val!"
    if /i "!key!"=="NVIDIA_API_KEY"     set "V_NKEY=!val!"
    if /i "!key!"=="NVIDIA_API_BASE_URL" set "V_NURL=!val!"
    if /i "!key!"=="NVIDIA_MODEL"       set "V_NMODEL=!val!"
    if /i "!key!"=="REDIS_HOST"         set "V_RHOST=!val!"
    if /i "!key!"=="REDIS_PORT"         set "V_RPORT=!val!"
    if /i "!key!"=="REDIS_PASSWORD"     set "V_RPASS=!val!"
)

if not defined V_RHOST set "V_RHOST=localhost"
if not defined V_RPORT set "V_RPORT=6379"

endlocal & (
    set "DB_PASSWORD=%V_DB%"
    set "JWT_SECRET=%V_JWT%"
    set "NVIDIA_API_KEY=%V_NKEY%"
    set "NVIDIA_API_BASE_URL=%V_NURL%"
    set "NVIDIA_MODEL=%V_NMODEL%"
    set "REDIS_HOST=%V_RHOST%"
    set "REDIS_PORT=%V_RPORT%"
    set "REDIS_PASSWORD=%V_RPASS%"
)

echo [env] Loaded from .env
echo [env]   DB_PASSWORD    : %DB_PASSWORD%
echo [env]   JWT_SECRET     : [set]
echo [env]   NVIDIA_API_KEY : [set]
echo [env]   Redis          : %REDIS_HOST%:%REDIS_PORT%
