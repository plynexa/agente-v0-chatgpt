@echo off
setlocal
cd /d "%~dp0"
echo Gerando o aplicativo nativo do Windows...
call gradlew.bat :desktopApp:createDistributable :desktopApp:packageExe :desktopApp:packageMsi --no-daemon
if errorlevel 1 (
  echo.
  echo ERRO: o executavel nao foi gerado. Consulte as mensagens acima.
  pause
  exit /b 1
)
echo.
echo Aplicativo gerado em:
echo %CD%\desktopApp\build\compose\binaries\main\exe
echo %CD%\desktopApp\build\compose\binaries\main\msi
echo.
echo Depois de gerar, ABRIR_AGENT_WINDOWS.bat abre diretamente o aplicativo compilado.
pause
endlocal
