#!/usr/bin/env bash
# ===================================================================
#  NEXLIB — pull the latest changes  (macOS / Linux)
#
#    ./update.sh
#
#  Fetches, switches to the working branch, and fast-forwards.
#  Local config files (local.properties, .env, keystore.properties,
#  serviceAccountKey.json) are git-ignored and are never touched.
# ===================================================================
set -euo pipefail
cd "$(dirname "$0")"

BRANCH="${1:-claude/repo-contents-review-6e0ub3}"

echo
echo "NEXLIB updater"
echo "=============="
echo "Repo:   $(pwd)"
echo "Branch: $BRANCH"
echo

# Refuse to clobber uncommitted work.
if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "[!] You have uncommitted changes:"
  echo
  git status --short
  echo
  echo "Choose one:"
  echo "  commit:  git add -A && git commit -m 'my changes'"
  echo "  park:    git stash          (restore later: git stash pop)"
  echo "  discard: git checkout -- ."
  echo
  echo "Nothing was changed. Re-run this script afterwards."
  exit 1
fi

echo "Fetching..."
git fetch origin --prune

echo "Switching to $BRANCH..."
git checkout "$BRANCH" 2>/dev/null || git checkout -b "$BRANCH" --track "origin/$BRANCH"

echo "Pulling..."
if ! git pull --ff-only origin "$BRANCH"; then
  echo
  echo "[!] Your branch has commits the remote does not, so a fast-forward"
  echo "    is not possible. Nothing was changed."
  echo "      keep your commits:  git pull --rebase origin $BRANCH"
  echo "      discard them:       git reset --hard origin/$BRANCH"
  exit 1
fi

echo
echo "============================================================"
echo " Updated. Latest commit:"
git --no-pager log -1 --format="   %h  %s"
echo
echo " NEXT STEP IN ANDROID STUDIO:"
echo "   File → Sync Project with Gradle Files"
echo "============================================================"
echo
