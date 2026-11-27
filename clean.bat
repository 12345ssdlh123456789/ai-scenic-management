@echo off
REM ============================================================
REM Scenic Management System - Clean Script
REM ASCII-only on purpose - see the note in start.bat.
REM
REM This file previously contained UTF-8 Chinese text in echo lines, which
REM cmd.exe mis-parses (it reads .bat in the OEM/ANSI codepage), producing
REM spurious "'xxx' is not recognized as an internal or external command"
REM errors. Now every .bat here is ASCII-only.
REM ============================================================
setlocal enabledelayedexpansion

set "ROOT_DIR=%~dp0"
if "%ROOT_DIR:~-1%"=="\" set "ROOT_DIR=%ROOT_DIR:~0,-1%"

echo ===========================================
echo   Scenic Management System - Clean
echo ===========================================
echo.
echo Project dir: %ROOT_DIR%
echo.
echo Choose what to clean:
echo   [1] Frontend dependencies  (myvue\node_modules)
echo   [2] Backend build output   (target)
echo   [3] Frontend build output  (myvue\dist)
echo   [4] All of the above
echo   [0] Exit
echo.

choice /c 12340 /n /m "Enter option [1/2/3/4/0]: "
set "OPTION=%ERRORLEVEL%"

if "%OPTION%"=="5" goto :END
if "%OPTION%"=="1" goto CLEAN_FRONTEND_DEPS
if "%OPTION%"=="2" goto CLEAN_BACKEND
if "%OPTION%"=="3" goto CLEAN_FRONTEND_DIST
if "%OPTION%"=="4" goto CLEAN_ALL

:CLEAN_FRONTEND_DEPS
if exist "%ROOT_DIR%\myvue\node_modules" (
    echo [1/1] Deleting myvue\node_modules ...
    rmdir /s /q "%ROOT_DIR%\myvue\node_modules" 2>nul
    echo Done.
) else (
    echo myvue\node_modules not found, skipping.
)
goto :END

:CLEAN_BACKEND
if exist "%ROOT_DIR%\target" (
    echo [1/1] Deleting target ...
    rmdir /s /q "%ROOT_DIR%\target" 2>nul
    echo Done.
) else (
    echo target not found, skipping.
)
goto :END

:CLEAN_FRONTEND_DIST
if exist "%ROOT_DIR%\myvue\dist" (
    echo [1/1] Deleting myvue\dist ...
    rmdir /s /q "%ROOT_DIR%\myvue\dist" 2>nul
    echo Done.
) else (
    echo myvue\dist not found, skipping.
)
goto :END

:CLEAN_ALL
echo [1/3] Cleaning frontend dependencies...
if exist "%ROOT_DIR%\myvue\node_modules" rmdir /s /q "%ROOT_DIR%\myvue\node_modules" 2>nul
echo [2/3] Cleaning backend build output...
if exist "%ROOT_DIR%\target" rmdir /s /q "%ROOT_DIR%\target" 2>nul
echo [3/3] Cleaning frontend build output...
if exist "%ROOT_DIR%\myvue\dist" rmdir /s /q "%ROOT_DIR%\myvue\dist" 2>nul
echo All done.

:END
echo.
echo ===========================================
echo   Clean finished
echo ===========================================
echo.
pause
