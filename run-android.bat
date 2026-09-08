@echo off
REM ===================================================================
REM  NEXLIB - build, install and launch the Android app in one step.
REM
REM  Double-click this file, or run it from a terminal in the repo root.
REM
REM  Needs: a connected phone with USB debugging on, OR a running
REM  emulator. Start one from Android Studio: Tools - Device Manager.
REM ===================================================================
setlocal EnableDelayedExpansion
cd /d "%~dp0"

set PKG=com.college.library
set ACTIVITY=com.college.library/.MainActivity

echo.
echo  NEXLIB - Android
echo  ================
echo.

REM --- Java -----------------------------------------------------------
where java >nul 2>&1
if errorlevel 1 (
  if defined JAVA_HOME (
    set "PATH=%JAVA_HOME%\bin;%PATH%"
  ) else (
    REM Android Studio ships its own JDK; use it if it is where it usually is.
    for %%D in (
      "%ProgramFiles%\Android\Android Studio\jbr"
      "%ProgramFiles%\Android\Android Studio\jre"
      "%LOCALAPPDATA%\Programs\Android Studio\jbr"
    ) do (
      if exist "%%~D\bin\java.exe" (
        set "JAVA_HOME=%%~D"
        set "PATH=%%~D\bin;!PATH!"
        goto :javafound
      )
    )
    echo  [!] Java 17 not found.
    echo      Install JDK 17, or set JAVA_HOME to Android Studio's bundled JDK:
    echo        "%ProgramFiles%\Android\Android Studio\jbr"
    goto :end
  )
)
:javafound

REM --- Locate adb -----------------------------------------------------
set "ADB="
where adb >nul 2>&1 && set "ADB=adb"

if not defined ADB if defined ANDROID_HOME (
  if exist "%ANDROID_HOME%\platform-tools\adb.exe" set "ADB=%ANDROID_HOME%\platform-tools\adb.exe"
)

if not defined ADB if exist "local.properties" (
  REM sdk.dir is stored escaped, e.g.  C\:\\Users\\me\\AppData\\Local\\Android\\Sdk
  for /f "usebackq tokens=1,* delims==" %%A in (`findstr /b "sdk.dir" local.properties`) do set "SDKDIR=%%B"
  if defined SDKDIR (
    set "SDKDIR=!SDKDIR:\\=\!"
    set "SDKDIR=!SDKDIR:\:=:!"
    if exist "!SDKDIR!\platform-tools\adb.exe" set "ADB=!SDKDIR!\platform-tools\adb.exe"
  )
)

if not defined ADB if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
  set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
)

if not defined ADB (
  echo  [!] Could not find adb.
  echo      Open the project in Android Studio once ^(it writes local.properties^),
  echo      or set ANDROID_HOME to your SDK folder.
  goto :end
)

REM --- Is a device attached? -------------------------------------------
echo  Looking for a device...
"%ADB%" start-server >nul 2>&1
set DEVICES=0
for /f "skip=1 tokens=1,2" %%A in ('"%ADB%" devices') do (
  if "%%B"=="device" set /a DEVICES+=1
)
if %DEVICES%==0 (
  echo.
  echo  [!] No device or emulator found.
  echo.
  echo      Either:
  echo        - start an emulator: Android Studio - Tools - Device Manager - Run
  echo        - or plug in a phone with Developer Options + USB debugging on,
  echo          and accept the "Allow USB debugging" prompt on the phone.
  echo.
  goto :end
)
echo  Found %DEVICES% device^(s^).

REM --- Build and install ------------------------------------------------
echo.
echo  Building and installing ^(first run downloads Gradle - be patient^)...
echo.
call gradlew.bat :app:installDebug
if errorlevel 1 (
  echo.
  echo  [!] Build failed. Read the first error above - later ones are usually
  echo      knock-on effects of it.
  goto :end
)

REM --- Launch -----------------------------------------------------------
echo.
echo  Launching %PKG%...
"%ADB%" shell am start -n %ACTIVITY% >nul 2>&1
if errorlevel 1 (
  echo  [!] Installed, but could not launch automatically.
  echo      Open NEXLIB from the app drawer.
  goto :end
)

echo.
echo  ============================================================
echo   Running on your device.
echo.
echo   To watch the app's logs, run in another terminal:
echo     adb logcat -s NEXLIB RealtimeSyncManager AndroidRuntime
echo  ============================================================

:end
echo.
pause
endlocal
