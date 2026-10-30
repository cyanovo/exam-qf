@echo off
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0deploy\windows\Manage.ps1" -Action Deploy
if errorlevel 1 (
  echo Deployment failed. Existing data was not reset.
  pause
  exit /b 1
)
pause
