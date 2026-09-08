@echo off
REM ===================================================================
REM  NEXLIB - run the web dashboard and the director portal.
REM ===================================================================
setlocal
cd /d "%~dp0\web-app"

echo.
echo  NEXLIB - Web + Director portal
echo  ==============================
echo.

where npm >nul 2>&1
if errorlevel 1 (
  echo  [!] Node.js not found. Install Node 20 or 22 from nodejs.org.
  goto :end
)

if not exist "node_modules" (
  echo  Installing dependencies ^(first run only, a few minutes^)...
  call npm install
  if errorlevel 1 goto :end
)

echo.
echo   College workspace : http://localhost:3000/dashboard
echo   Director portal   : http://localhost:3000/director
echo.
echo   Press Ctrl+C to stop.
echo.
call npm run dev

:end
echo.
pause
endlocal
