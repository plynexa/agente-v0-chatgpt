@echo off
setlocal
cd /d "%~dp0"
echo Iniciando o painel Agent V0...
set "APP=%CD%\desktopApp\build\compose\binaries\main\app\AgentV0ChatGPT\AgentV0ChatGPT.exe"
if exist "%APP%" (
  start "" "%APP%"
  exit /b 0
)
call gradlew.bat :desktopApp:run --no-daemon
if errorlevel 1 (
  echo.
  echo ERRO: o painel nao iniciou. Consulte as mensagens acima.
  pause
)
endlocal
