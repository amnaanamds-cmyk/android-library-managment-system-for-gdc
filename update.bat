@echo off
REM ===================================================================
REM  NEXLIB - update EVERYTHING with one double-click.
REM
REM  All four apps live in this one repository, so one pull updates the
REM  Android app, the Windows desktop app, the web dashboard and the
REM  director portal together.
REM
REM  This script then does the follow-up work a plain `git pull` leaves
REM  behind: it looks at WHICH files changed and reinstalls only what
REM  actually needs reinstalling. Forgetting that step is why a pull
REM  sometimes appears to have done nothing.
REM
REM  Your local config - local.properties, .env, keystore.properties,
REM  serviceAccountKey.json - is git-ignored and is never touched.
REM ===================================================================

setlocal EnableDelayedExpansion
cd /d "%~dp0"

set BRANCH=claude/repo-contents-review-6e0ub3

echo.
echo  ============================================================
echo   NEXLIB updater
echo  ============================================================
echo   Folder : %CD%
echo   Branch : %BRANCH%
echo.

REM --- Refuse to clobber uncommitted work -----------------------------
git diff --quiet
if errorlevel 1 goto dirty
git diff --cached --quiet
if errorlevel 1 goto dirty
goto clean

:dirty
echo  [!] You have uncommitted changes:
echo.
git status --short
echo.
echo  Pick one, then run this again:
echo    keep them   :  git stash          ^(restore later: git stash pop^)
echo    save them   :  git add -A ^&^& git commit -m "my changes"
echo    throw away  :  git checkout -- .
echo.
echo  Nothing was changed.
goto end

:clean
REM --- Remember where we were, so we can see what changed --------------
for /f "delims=" %%i in ('git rev-parse HEAD') do set BEFORE=%%i

echo  [1/3] Fetching...
git fetch origin --prune
if errorlevel 1 goto neterr

echo  [2/3] Switching to %BRANCH% and pulling...
git checkout %BRANCH% 2>nul
if errorlevel 1 git checkout -b %BRANCH% --track origin/%BRANCH%
if errorlevel 1 goto brancherr

git pull --ff-only origin %BRANCH%
if errorlevel 1 goto divergederr

for /f "delims=" %%i in ('git rev-parse HEAD') do set AFTER=%%i

if "%BEFORE%"=="%AFTER%" (
  echo.
  echo  Already up to date - nothing new to install.
  goto summary
)

REM --- Which files changed? --------------------------------------------
git diff --name-only %BEFORE% %AFTER% > "%TEMP%\nexlib_changed.txt"

echo.
echo  Changed files:
type "%TEMP%\nexlib_changed.txt"
echo.
echo  [3/3] Reinstalling what changed...
echo.

set NEED_GRADLE=0
set NEED_RULES=0

REM --- Backend (Cloud Functions) ---------------------------------------
findstr /b /c:"functions/" "%TEMP%\nexlib_changed.txt" >nul
if not errorlevel 1 (
  echo   - Backend changed: npm install + build
  pushd functions
  call npm install --silent
  call npm run build
  popd
)

REM --- Web dashboard + director portal ---------------------------------
findstr /c:"web-app/package.json" "%TEMP%\nexlib_changed.txt" >nul
if not errorlevel 1 (
  echo   - Web dependencies changed: npm install
  pushd web-app
  call npm install --silent
  popd
) else (
  findstr /b /c:"web-app/" "%TEMP%\nexlib_changed.txt" >nul
  if not errorlevel 1 echo   - Web code changed: restart "npm run dev" if it is running
)

REM --- Windows desktop app ---------------------------------------------
findstr /c:"gdc_desktop/requirements.txt" "%TEMP%\nexlib_changed.txt" >nul
if not errorlevel 1 (
  echo   - Desktop dependencies changed: pip install
  pushd gdc_desktop
  call python -m pip install -q -r requirements.txt
  popd
) else (
  findstr /b /c:"gdc_desktop/" "%TEMP%\nexlib_changed.txt" >nul
  if not errorlevel 1 echo   - Desktop code changed: just restart the desktop app
)

REM --- Android app ------------------------------------------------------
findstr /r /c:"\.kt$" /c:"\.kts$" /c:"^gradle" /c:"^app/" /c:"^shared/" "%TEMP%\nexlib_changed.txt" >nul
if not errorlevel 1 set NEED_GRADLE=1

REM --- Firestore rules / indexes ---------------------------------------
findstr /r /c:"^firestore\." "%TEMP%\nexlib_changed.txt" >nul
if not errorlevel 1 set NEED_RULES=1

del "%TEMP%\nexlib_changed.txt" >nul 2>&1

:summary
echo.
echo  ============================================================
echo   Updated to:
git --no-pager log -1 --format="   %%h  %%s"
echo.

if "%NEED_GRADLE%"=="1" (
  echo   ANDROID - one manual step:
  echo     Android Studio: File - Sync Project with Gradle Files
  echo.
)
if "%NEED_RULES%"=="1" (
  echo   FIRESTORE RULES changed. To deploy them:
  echo     firebase deploy --only firestore:rules,firestore:indexes
  echo.
)

echo   Then run any app:
echo     run-android.bat    Android  ^(build + install + launch^)
echo     run-desktop.bat    Windows desktop
echo     run-web.bat        Web dashboard + Director portal
echo     run.bat            menu for all of them
echo  ============================================================
goto end

:neterr
echo  [!] Could not reach GitHub. Check your internet connection.
goto end

:brancherr
echo  [!] Could not switch to %BRANCH%. Does it exist on the remote?
echo      Try:  git branch -r
goto end

:divergederr
echo  [!] Your branch has commits the remote does not, so a fast-forward
echo      is not possible. Nothing was changed.
echo.
echo      keep your commits :  git pull --rebase origin %BRANCH%
echo      discard them      :  git reset --hard origin/%BRANCH%
goto end

:end
echo.
pause
endlocal
