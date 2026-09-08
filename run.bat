@echo off
REM  NEXLIB - pick what to run.
setlocal
cd /d "%~dp0"

:menu
cls
echo.
echo   NEXLIB
echo   ======
echo.
echo    1. Android app        ^(build, install, launch^)
echo    2. Desktop client     ^(Python + PyQt6^)
echo    3. Web + Director     ^(http://localhost:3000^)
echo    4. Pull latest changes
echo    5. Exit
echo.
set /p choice="  Choose 1-5: "

if "%choice%"=="1" call "%~dp0run-android.bat" & goto menu
if "%choice%"=="2" call "%~dp0run-desktop.bat"  & goto menu
if "%choice%"=="3" call "%~dp0run-web.bat"      & goto menu
if "%choice%"=="4" call "%~dp0update.bat"       & goto menu
if "%choice%"=="5" goto :eof
goto menu
