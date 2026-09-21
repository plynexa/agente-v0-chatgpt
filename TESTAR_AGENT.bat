@echo off
setlocal
cd /d "%~dp0"
echo Executando testes principais e compilando o painel...
call gradlew.bat :shared:desktopTest :desktopApp:compileKotlinDesktop --no-daemon
if errorlevel 1 (
  echo.
  echo TESTES FALHARAM. Consulte as mensagens acima.
) else (
  echo.
  echo TESTES CONCLUIDOS COM SUCESSO.
)
pause
endlocal
