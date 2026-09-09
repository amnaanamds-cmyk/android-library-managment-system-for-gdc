#!/usr/bin/env bash
# ===================================================================
#  NEXLIB — update EVERYTHING (macOS / Linux)
#
#  All four apps live in this one repository, so one pull updates the
#  Android app, the desktop app, the web dashboard and the director
#  portal together. This then reinstalls only what actually changed —
#  forgetting that step is why a pull can appear to have done nothing.
#
#  Local config (local.properties, .env, keystore.properties,
#  serviceAccountKey.json) is git-ignored and never touched.
# ===================================================================
set -euo pipefail
cd "$(dirname "$0")"

BRANCH="${1:-claude/repo-contents-review-6e0ub3}"

echo
echo "============================================================"
echo " NEXLIB updater"
echo "============================================================"
echo " Folder : $(pwd)"
echo " Branch : $BRANCH"
echo

if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "[!] You have uncommitted changes:"; echo
  git status --short; echo
  echo "Pick one, then run this again:"
  echo "  keep them  : git stash          (restore later: git stash pop)"
  echo "  save them  : git add -A && git commit -m 'my changes'"
  echo "  throw away : git checkout -- ."
  exit 1
fi

BEFORE=$(git rev-parse HEAD)

echo "[1/3] Fetching..."
git fetch origin --prune

echo "[2/3] Switching to $BRANCH and pulling..."
git checkout "$BRANCH" 2>/dev/null || git checkout -b "$BRANCH" --track "origin/$BRANCH"
if ! git pull --ff-only origin "$BRANCH"; then
  echo
  echo "[!] Your branch has commits the remote does not; fast-forward impossible."
  echo "    keep them   : git pull --rebase origin $BRANCH"
  echo "    discard them: git reset --hard origin/$BRANCH"
  exit 1
fi

AFTER=$(git rev-parse HEAD)
NEED_GRADLE=0
NEED_RULES=0

if [ "$BEFORE" = "$AFTER" ]; then
  echo
  echo "Already up to date — nothing new to install."
else
  CHANGED=$(git diff --name-only "$BEFORE" "$AFTER")
  echo
  echo "Changed files:"; echo "$CHANGED" | sed 's/^/  /'
  echo
  echo "[3/3] Reinstalling what changed..."
  echo

  if grep -q '^functions/' <<< "$CHANGED"; then
    echo "  - Backend changed: npm install + build"
    (cd functions && npm install --silent && npm run build)
  fi

  if grep -q '^web-app/package.json' <<< "$CHANGED"; then
    echo "  - Web dependencies changed: npm install"
    (cd web-app && npm install --silent)
  elif grep -q '^web-app/' <<< "$CHANGED"; then
    echo "  - Web code changed: restart 'npm run dev' if it is running"
  fi

  if grep -q '^gdc_desktop/requirements.txt' <<< "$CHANGED"; then
    echo "  - Desktop dependencies changed: pip install"
    (cd gdc_desktop && python3 -m pip install -q -r requirements.txt)
  elif grep -q '^gdc_desktop/' <<< "$CHANGED"; then
    echo "  - Desktop code changed: just restart the desktop app"
  fi

  grep -qE '\.kts?$|^gradle|^app/|^shared/' <<< "$CHANGED" && NEED_GRADLE=1
  grep -qE '^firestore\.'                    <<< "$CHANGED" && NEED_RULES=1
fi

echo
echo "============================================================"
echo " Updated to:"
git --no-pager log -1 --format="   %h  %s"
echo
[ "$NEED_GRADLE" = 1 ] && { echo " ANDROID — one manual step:"; echo "   Android Studio: File → Sync Project with Gradle Files"; echo; }
[ "$NEED_RULES"  = 1 ] && { echo " FIRESTORE RULES changed. To deploy:"; echo "   firebase deploy --only firestore:rules,firestore:indexes"; echo; }
echo " Then run any app:  ./run-android.bat  ./run-desktop.bat  ./run-web.bat"
echo "============================================================"
echo
