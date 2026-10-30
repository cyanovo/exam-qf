@echo off
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0deploy\windows\Manage.ps1" -Action Start
if errorlevel 1 (
  echo Startup failed. Read the error above and .runtime-release\logs.
  pause
  exit /b 1
)
