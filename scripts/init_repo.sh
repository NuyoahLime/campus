#!/bin/sh
set -e
# Navigate to project root (assumes this script sits in scripts/)
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

if [ -d .git ]; then
  echo ".git already exists — skipping git init"
else
  git init
  git add .
  git commit -m "chore: initialize campus challenge platform"
  echo "Repository initialized and initial commit created."
fi
