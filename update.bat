@echo off
REM ===================================================================
REM  NEXLIB - pull the latest changes
REM
REM  Double-click this file, or run it from a terminal in the repo root.
REM  It fetches, switches to the working branch, and fast-forwards.
REM
REM  Your local config files (local.properties, .env, keystore.properties,
REM  serviceAccountKey.json) are git-ignored and are never touched.
REM ===================================================================

setlocal
cd /d "%~dp0"

set BRANCH=claude/repo-contents-review-6e0ub3

echo.
echo  NEXLIB updater
echo  ==============
echo  Repo:   %CD%
echo  Branch: %BRANCH%
echo.

REM --- Refuse to clobber uncommitted work -----------------------------
git diff --quiet
if errorlevel 1 goto dirty
git diff --cached --quiet
if errorlevel 1 goto dirty
goto clean

:dirty
echo  [!] You have uncommitted changes.
echo.
git status --short
echo.
echo  Choose one:
echo    - commit them:  git add -A ^&^& git commit -m "my changes"
echo    - park them:    git stash        ^(restore later with: git stash pop^)
echo    - discard them: git checkout -- .
echo.
echo  Nothing was changed. Re-run this script afterwards.
goto end

:clean
echo  Fetching...
git fetch origin --prune
if errorlevel 1 goto neterr

echo  Switching to %BRANCH%...
git checkout %BRANCH% 2>nul
if errorlevel 1 git checkout -b %BRANCH% --track origin/%BRANCH%
if errorlevel 1 goto brancherr

echo  Pulling...
git pull --ff-only origin %BRANCH%
if errorlevel 1 goto divergederr

echo.
echo  ============================================================
echo   Updated. Latest commit:
git --no-pager log -1 --format="   %%h  %%s"
echo.
echo   NEXT STEP IN ANDROID STUDIO:
echo     File - Sync Project with Gradle Files
echo   ^(or the elephant icon in the toolbar^)
echo.
echo   If Gradle still complains, use:
echo     File - Invalidate Caches... - Invalidate and Restart
echo  ============================================================
goto end

:neterr
echo  [!] Could not reach GitHub. Check your internet connection.
goto end

:brancherr
echo  [!] Could not switch to %BRANCH%.
echo      Does it exist on the remote? Try: git branch -r
goto end

:divergederr
echo  [!] Your branch has commits that the remote does not, so a
echo      fast-forward is not possible. Nothing was changed.
echo.
echo      To keep your commits:      git pull --rebase origin %BRANCH%
echo      To discard them entirely:  git reset --hard origin/%BRANCH%
goto end

:end
echo.
pause
endlocal
