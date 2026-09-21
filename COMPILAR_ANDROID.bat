@echo off
setlocal
cd /d "%~dp0"
echo Compilando APK Android debug...
call gradlew.bat :androidApp:assembleDebug --no-daemon
if errorlevel 1 (
  echo.
  echo ERRO: o APK nao foi gerado. Consulte as mensagens acima.
) else (
  echo.
  echo APK criado em:
  echo %CD%\androidApp\build\outputs\apk\debug\androidApp-debug.apk
)
pause
endlocal
