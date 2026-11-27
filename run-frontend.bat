@echo off
REM ============================================================
REM Frontend launcher (called by start.bat, can also run standalone)
REM ASCII-only on purpose - see the note in start.bat.
REM ============================================================
setlocal

set "ROOT_DIR=%~dp0"
if "%ROOT_DIR:~-1%"=="\" set "ROOT_DIR=%ROOT_DIR:~0,-1%"
cd /d "%ROOT_DIR%\myvue"

if not exist "node_modules" (
    echo [frontend] First run - installing dependencies...
    call npm install
    if errorlevel 1 (
        echo [frontend] [ERROR] npm install failed
        pause
        exit /b 1
    )
)

echo [frontend] Project dir : %ROOT_DIR%\myvue
echo [frontend] Starting Vite dev server on port 5173 ...
echo.

call npm run dev

echo.
echo [frontend] Process exited with code %ERRORLEVEL%
pause
