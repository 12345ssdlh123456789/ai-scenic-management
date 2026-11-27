@echo off
REM ============================================================
REM Backend launcher (called by start.bat, can also run standalone)
REM ASCII-only on purpose - see the note in start.bat.
REM
REM KEY POINT: only set variables when they have a value.
REM   Writing "set REDIS_PORT=%REDIS_PORT%" while it is empty injects an
REM   empty string, so Spring's ${REDIS_PORT:6379} receives "" instead of
REM   the default and startup fails with:
REM     NumberFormatException: For input string: ""
REM ============================================================
setlocal enabledelayedexpansion

set "ROOT_DIR=%~dp0"
if "%ROOT_DIR:~-1%"=="\" set "ROOT_DIR=%ROOT_DIR:~0,-1%"
cd /d "%ROOT_DIR%"

REM ---- Load .env if start.bat has not injected values ----
if not defined DB_PASSWORD (
    if exist "%ROOT_DIR%\.env" (
        for /f "usebackq eol=# tokens=1,* delims==" %%a in ("%ROOT_DIR%\.env") do (
            set "key=%%a"
            set "val=%%b"
            for /f "tokens=* delims= " %%k in ("!key!") do set "key=%%k"
            if not "!key!"=="" set "!key!=!val!"
        )
    )
)

REM ---- Defaults (only when undefined) ----
if not defined REDIS_HOST set "REDIS_HOST=localhost"
if not defined REDIS_PORT set "REDIS_PORT=6379"

echo [backend] Project dir : %ROOT_DIR%
echo [backend] Redis       : %REDIS_HOST%:%REDIS_PORT%
if not defined DB_PASSWORD    echo [backend] [WARN] DB_PASSWORD not set - database will fail
if not defined JWT_SECRET     echo [backend] [WARN] JWT_SECRET not set - app will refuse to start
if not defined NVIDIA_API_KEY echo [backend] [WARN] NVIDIA_API_KEY not set - AI chat unavailable
echo [backend] Starting Spring Boot on port 9090 ...
echo.

REM Prefer Maven Wrapper so this does not depend on a machine-wide mvn
if exist "%ROOT_DIR%\mvnw.cmd" (
    call "%ROOT_DIR%\mvnw.cmd" spring-boot:run
) else (
    call mvn spring-boot:run
)

echo.
echo [backend] Process exited with code %ERRORLEVEL%
pause
