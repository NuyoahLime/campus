Param()
# Navigate to project root (script is expected in scripts\)
$root = Split-Path -Path $PSScriptRoot -Parent
Set-Location $root

if (Test-Path -Path .git) {
    Write-Host ".git already exists — skipping git init"
} else {
    git init
    git add .
    git commit -m "chore: initialize campus challenge platform"
    Write-Host "Repository initialized and initial commit created."
}
