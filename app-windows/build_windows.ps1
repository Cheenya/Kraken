$ErrorActionPreference = "Stop"

Set-Location $PSScriptRoot

if (-not (Test-Path ".venv\Scripts\python.exe")) {
    py -3 -m venv .venv
}

$python = ".venv\Scripts\python.exe"
& $python -m pip install --upgrade pip
& $python -m pip install -r requirements.txt -r requirements-build.txt
& $python -m PyInstaller --noconfirm --windowed --name KrakenWindows --paths . kraken_windows\__main__.py

Write-Host "Windows-сборка записана в dist\KrakenWindows"
