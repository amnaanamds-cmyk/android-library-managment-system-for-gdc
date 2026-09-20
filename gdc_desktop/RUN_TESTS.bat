@echo off
setlocal enabledelayedexpansion

REM ============================================================================
REM  RUN_TESTS.bat - double-click launcher for the NEXLIB test tools.
REM
REM  The tools are ordinary Python scripts, but they must run from the
REM  gdc_desktop folder: they import config.py from the folder above scripts/,
REM  and a relative path typed from anywhere else fails with "No such file or
REM  directory". The cd below uses %~dp0 - the folder THIS FILE lives in - so
REM  the launcher works no matter where it is started from, including a
REM  double-click from Explorer or a desktop shortcut.
REM ============================================================================

cd /d "%~dp0"

REM Python is "python" on most installs and "py" where the launcher is used.
set PYTHON=
where python >nul 2>&1 && set PYTHON=python
if "%PYTHON%"=="" (where py >nul 2>&1 && set PYTHON=py)
if "%PYTHON%"=="" (
    echo.
    echo   Python was not found on this computer.
    echo   Install it from python.org and tick "Add Python to PATH".
    echo.
    pause
    exit /b 1
)

if not exist "scripts\nexlib_test.py" (
    echo.
    echo   Cannot find scripts\nexlib_test.py next to this file.
    echo   This launcher must stay inside the gdc_desktop folder.
    echo   Current folder: %CD%
    echo.
    pause
    exit /b 1
)

:menu
cls
echo.
echo   ============================================================
echo      NEXLIB - Test and Diagnosis Tools
echo   ============================================================
echo      Folder: %CD%
echo.
echo      1.  Check this installation        (is it set up correctly?)
echo      2.  Check for crashes              (what has been failing?)
echo      3.  Diagnose sync                  (WHERE is sync breaking?)
echo      4.  Load test                      (what will it cost at scale?)
echo      5.  Confirm everything is cleared  (after a wipe)
echo.
echo      0.  Exit
echo.
set /p choice=   Choose 0-5 and press Enter:

if "%choice%"=="1" goto verify
if "%choice%"=="2" goto crash
if "%choice%"=="3" goto sync
if "%choice%"=="4" goto load
if "%choice%"=="5" goto empty
if "%choice%"=="0" exit /b 0
goto menu

:verify
cls
echo Running installation check...
echo.
%PYTHON% scripts\verify_system.py
goto done

:crash
cls
echo Scanning the application log for crashes...
echo.
%PYTHON% scripts\nexlib_test.py crash
goto done

:sync
cls
echo.
echo   Sync diagnosis signs in as a real librarian account and walks the
echo   eight stages of sync, stopping at the first one that fails.
echo.
echo   Use a COLLEGE account - not the directorate one.
echo.
set /p email=   Email:
set /p pass=   Password:
cls
echo Diagnosing sync for %email%...
echo.
%PYTHON% scripts\nexlib_test.py sync --email "%email%" --password "%pass%"
goto done

:load
cls
echo.
echo   The load test WRITES synthetic colleges to Firestore to measure what
echo   sync really costs. Every one is named LOADTEST-nnn, and you are asked
echo   to remove them when the run finishes. Real college data is never
echo   touched.
echo.
echo   Start small: 3 colleges of 2000 books takes a few minutes.
echo.
set /p ncol=   How many colleges  [3]:
set /p nbook=  Books per college  [2000]:
if "%ncol%"=="" set ncol=3
if "%nbook%"=="" set nbook=2000
cls
echo Seeding %ncol% college(s) with %nbook% books each...
echo.
%PYTHON% scripts\nexlib_test.py load --colleges %ncol% --books %nbook%
echo.
echo   Remove the synthetic colleges now? (recommended)
set /p clean=   Type Y to clean up, anything else to keep them:
if /i "%clean%"=="Y" %PYTHON% scripts\nexlib_test.py load --cleanup
goto done

:empty
cls
echo Checking that everything really was cleared...
echo.
%PYTHON% scripts\verify_system.py --expect-empty
goto done

:done
echo.
echo   ------------------------------------------------------------
echo.
pause
goto menu
