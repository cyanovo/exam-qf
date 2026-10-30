@echo off
setlocal
title Exam Frontend - Keep this window open
cd /d "%~dp0cyan-exam-vue"
if errorlevel 1 goto failed
if not exist "D:\app\nodejs\node.exe" (
  echo Node.js not found: D:\app\nodejs\node.exe
  goto failed
)
if not exist "node_modules\vite\bin\vite.js" (
  echo Vite dependencies are missing.
  goto failed
)
set "EXAM_API_TARGET=http://127.0.0.1:9010"
set "BROWSER=none"
echo Starting exam frontend...
echo Open http://127.0.0.1:5178 after Vite reports ready.
echo Keep this window open while using the exam system.
"D:\app\nodejs\node.exe" node_modules\vite\bin\vite.js --host 127.0.0.1 --port 5178 --strictPort
if errorlevel 1 goto failed
exit /b 0

:failed
echo.
echo Startup failed. Please send the error shown above.
pause
exit /b 1
