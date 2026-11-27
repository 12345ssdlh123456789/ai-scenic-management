@echo off
REM ============================================================
REM Scenic Management System - One-Click Start
REM
REM NOTE: This file is intentionally ASCII-only.
REM   cmd.exe parses .bat files using the OEM/ANSI codepage. UTF-8 Chinese
REM   text (including inside REM comments) gets mangled and can spill into
REM   execution when output is redirected, producing errors like:
REM     'xxx' is not recognized as an internal or external command
REM   Keep every .bat in this project ASCII-only.
REM ============================================================
setlocal enabledelayedexpansion

set "ROOT_DIR=%~dp0"
if "%ROOT_DIR:~-1%"=="\" set "ROOT_DIR=%ROOT_DIR:~0,-1%"

echo ===========================================
echo   Scenic Management System - Start
echo ===========================================
echo.
echo Project dir: %ROOT_DIR%
echo.

REM ---------- 1. Load .env ----------
echo [1/6] Loading config...
if not exist "%ROOT_DIR%\.env" (
    if exist "%ROOT_DIR%\.env.example" (
        copy /y "%ROOT_DIR%\.env.example" "%ROOT_DIR%\.env" >nul
        echo   .env created from .env.example - please review it
    ) else (
        echo   [ERROR] Missing .env and .env.example
        pause
        exit /b 1
    )
)

for /f "usebackq eol=# tokens=1,* delims==" %%a in ("%ROOT_DIR%\.env") do (
    set "key=%%a"
    set "val=%%b"
    for /f "tokens=* delims= " %%k in ("!key!") do set "key=%%k"
    if not "!key!"=="" set "!key!=!val!"
)

REM Redis defaults are REQUIRED. If left empty, "set REDIS_PORT=%REDIS_PORT%"
REM would inject an empty string and Spring's ${REDIS_PORT:6379} would receive
REM "" instead of the default, failing with:
REM   NumberFormatException: For input string: ""
if not defined REDIS_HOST set "REDIS_HOST=localhost"
if not defined REDIS_PORT set "REDIS_PORT=6379"

echo   Config loaded: DB_PASSWORD / JWT_SECRET / NVIDIA_API_KEY
echo   Redis: %REDIS_HOST%:%REDIS_PORT%
if not defined DB_PASSWORD echo   [WARN] DB_PASSWORD not set - database will fail
if not defined JWT_SECRET  echo   [WARN] JWT_SECRET not set - app will refuse to start
echo.

REM ---------- 2. Toolchain ----------
echo [2/6] Checking environment...

where java >nul 2>&1
if errorlevel 1 (
    echo   [ERROR] java not found. Install JDK 17+
    pause
    exit /b 1
)
for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do set "JAVA_VER=%%v"
echo   Java: !JAVA_VER!

where node >nul 2>&1
if errorlevel 1 (
    echo   [ERROR] node not found. Install Node.js 18+
    pause
    exit /b 1
)
for /f %%v in ('node -v') do set "NODE_VER=%%v"
echo   Node: !NODE_VER!
echo.

REM ---------- 3. Dependency services ----------
echo [3/6] Checking services...

call :CHECK_PORT 3306
if "!PORT_OPEN!"=="1" (
    echo   MySQL  [3306]: OK
) else (
    echo   [ERROR] MySQL [3306] not running. Start MySQL first.
    pause
    exit /b 1
)

call :CHECK_PORT 6379
if "!PORT_OPEN!"=="1" (
    echo   Redis  [6379]: OK
) else (
    echo   [WARN] Redis [6379] not running.
    echo          Cache/rate-limit will degrade and token refresh will fail.
    echo          Start it with: net start Redis
)
echo.

REM ---------- 4. Frontend dependencies ----------
echo [4/6] Checking frontend deps...
if not exist "%ROOT_DIR%\myvue\node_modules" (
    echo   Installing frontend deps ^(first run, may take minutes^)...
    pushd "%ROOT_DIR%\myvue"
    call npm install
    if errorlevel 1 (
        echo   [ERROR] npm install failed
        popd
        pause
        exit /b 1
    )
    popd
) else (
    echo   node_modules present, skipping npm install
)
echo.

REM ---------- 5. Backend ----------
echo [5/6] Starting backend [port 9090]...
start "Scenic-Backend" cmd /k ""%ROOT_DIR%\run-backend.bat""

echo   Waiting for backend (up to 120s)...
set "BACKEND_UP=0"
REM NOTE: do NOT use "timeout /t" here.
REM   timeout.exe requires a console input handle; when the script is run
REM   from a non-interactive context (CI, an IDE task, a piped shell,
REM   some terminals) it fails instantly with:
REM     ERROR: Input redirection is not supported, exiting the process immediately.
REM   The loop below would then burn through all 60 iterations in about one
REM   second and print a misleading "[WARN] Backend not ready within 120s"
REM   long before Spring Boot had any chance to bind the port.
REM   Measured: 60x timeout = 1.1s instead of 120s.
REM   "ping -n" is the portable delay that works without a console.
for /l %%i in (1,1,60) do (
    if "!BACKEND_UP!"=="0" (
        ping -n 3 127.0.0.1 >nul 2>&1
        call :CHECK_PORT 9090
        set "BACKEND_UP=!PORT_OPEN!"
        REM report progress roughly every 20s so it is clear whether
        REM the backend is slow or actually stuck
        set /a TICK=%%i %% 10
        if "!TICK!"=="0" echo     ...still waiting ^(%%i/60^)
    )
)
if "!BACKEND_UP!"=="1" (
    echo   Backend ready: http://localhost:9090
) else (
    echo   [WARN] Backend did not bind port 9090 within 120s.
    echo          Check the "Scenic-Backend" window for the real error
    echo          ^(commonly: MySQL/Redis not running, or a wrong DB_PASSWORD^).
)
echo.

REM ---------- 6. Frontend ----------
echo [6/6] Starting frontend [port 5173]...
start "Scenic-Frontend" cmd /k ""%ROOT_DIR%\run-frontend.bat""

echo.
echo ===========================================
echo   Startup complete
echo ===========================================
echo.
echo   Frontend: http://localhost:5173
echo   Backend : http://localhost:9090
echo   Health  : http://localhost:9090/actuator/health
echo.
echo   To stop: close the two windows, or run stop.bat
echo.
echo   Default accounts: admin / 123456  (SUPER_ADMIN)
echo                     testuser / 123456
echo.
pause
exit /b 0

REM ---- Detect whether a TCP port is LISTENING ----
REM Uses netstat rather than PowerShell Test-NetConnection: the latter's
REM quotes and pipes get mangled inside a cmd for/f block.
:CHECK_PORT
set "PORT_OPEN=0"
for /f "tokens=*" %%L in ('netstat -an ^| findstr /c:"LISTENING" ^| findstr /c:":%~1 "') do set "PORT_OPEN=1"
exit /b 0
