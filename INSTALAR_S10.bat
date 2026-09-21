@echo off
setlocal
cd /d "%~dp0"
where adb >nul 2>nul
if errorlevel 1 (
  echo ERRO: ADB nao foi encontrado no PATH. Instale Android Platform Tools.
  pause
  exit /b 1
)
set "APK=%CD%\androidApp\build\outputs\apk\debug\androidApp-debug.apk"
if not exist "%APK%" (
  echo APK ausente. Executando a compilacao primeiro...
  call gradlew.bat :androidApp:assembleDebug --no-daemon
  if errorlevel 1 goto :failure
)
for /f "skip=1 tokens=1,2" %%A in ('adb devices') do if "%%B"=="device" set "DEVICE_FOUND=1"
if not defined DEVICE_FOUND (
  echo ERRO: nenhum Android autorizado foi detectado pelo ADB.
  echo Conecte o S10 por USB e aceite a autorizacao de depuracao.
  pause
  exit /b 1
)
adb install -r "%APK%"
if errorlevel 1 goto :failure
echo.
echo APK instalado/atualizado com sucesso no S10.
pause
exit /b 0
:failure
echo.
echo ERRO: compilacao ou instalacao falhou. Consulte as mensagens acima.
pause
exit /b 1
