@echo off
REM ============================================================
REM Scenic Management System - Stop services
REM ASCII-only on purpose - see the note in start.bat.
REM
REM Frees ports 9090 (backend) and 5173 (frontend).
REM Previously you had to hunt down the two spawned windows manually,
REM which was easy to miss and easy to get wrong.
REM
REM Implementation note: an earlier attempt collected PIDs into a SEEN
REM string and tested membership with "echo !SEEN! | findstr ..." inside an
REM if-block. That is fragile in cmd: with SEEN empty the echo expands to a
REM bare "echo" before the pipe, and pipes/&& inside a parenthesised if
REM inside a for body fail with "The syntax of the command is incorrect".
REM Here we instead resolve the PID once per port from netstat (taking the
REM first match) and kill it, which needs no string bookkeeping at all.
REM ============================================================
setlocal enabledelayedexpansion

echo ===========================================
echo   Scenic Management System - Stop
echo ===========================================
echo.

set "KILLED=0"

call :KILL_PORT 9090 backend
call :KILL_PORT 5173 frontend

REM Close the spawned console windows if they are still around
taskkill /FI "WINDOWTITLE eq Scenic-Backend*" /F >nul 2>&1
taskkill /FI "WINDOWTITLE eq Scenic-Frontend*" /F >nul 2>&1

echo.
if "!KILLED!"=="1" (
    echo   Services stopped.
) else (
    echo   Nothing running ^(neither 9090 nor 5173 was listening^).
)
echo.
pause
exit /b 0

REM ------------------------------------------------------------
REM :KILL_PORT <port> <label>
REM Resolve the listening PID for a port and terminate it.
REM netstat may print the same PID on both the IPv4 and IPv6 row; killing
REM an already-dead PID simply fails harmlessly, and we only report the
REM first successful kill, so no de-duplication is required.
REM ------------------------------------------------------------
:KILL_PORT
set "FOUND=0"
for /f "tokens=5" %%p in ('netstat -ano ^| findstr /c:"LISTENING" ^| findstr /c:":%~1 "') do (
    if "!FOUND!"=="0" (
        echo   Stopping %~2 PID %%p [port %~1]
        taskkill /PID %%p /F >nul 2>&1
        set "FOUND=1"
        set "KILLED=1"
    )
)
exit /b 0
