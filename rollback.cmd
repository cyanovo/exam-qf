@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0deploy\windows\Manage.ps1" -Action Rollback
pause
