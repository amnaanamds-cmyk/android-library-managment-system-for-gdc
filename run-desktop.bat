@echo off
REM ===================================================================
REM  NEXLIB - run the Python/PyQt6 desktop client.
REM ===================================================================
setlocal
cd /d "%~dp0\gdc_desktop"

echo.
echo  NEXLIB - Desktop
echo  ================
echo.

where python >nul 2>&1
if errorlevel 1 (
  echo  [!] Python not found. Install Python 3.10+ and tick
  echo      "Add python.exe to PATH" in the installer.
  goto :end
)

python -c "import PyQt6" >nul 2>&1
if errorlevel 1 (
  echo  Installing dependencies ^(first run only^)...
  python -m pip install -r requirements.txt
  if errorlevel 1 goto :end
)

if not exist ".env" (
  echo  [!] gdc_desktop\.env is missing.
  echo      Copy .env.example to .env and fill in COLLEGE_ID and your
  echo      Firebase keys. Without it the app starts in offline mock mode.
  echo.
)

echo  Starting...
python main.py

:end
echo.
pause
endlocal
