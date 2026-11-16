@echo off
setlocal
chcp 65001 >nul
set /p PEDIDO=Digite o numero do pedido visivel no cabecalho do MLOG: 
if "%PEDIDO%"=="" (
  echo Informe o numero exato, incluindo zeros iniciais.
  pause
  exit /b 1
)
echo.
echo Abra no coletor a tela do MLOG com o pedido %PEDIDO% em destaque.
echo Pressione qualquer tecla para capturar a arvore de acessibilidade.
pause >nul
adb devices
adb shell uiautomator dump /sdcard/bridge_pedido_tela.xml
if errorlevel 1 goto :fail
adb pull /sdcard/bridge_pedido_tela.xml "%TEMP%\bridge_pedido_tela.xml" >nul
if errorlevel 1 goto :fail
findstr /C:"%PEDIDO%" "%TEMP%\bridge_pedido_tela.xml" >nul
if errorlevel 1 (
 echo [ATENCAO] O numero nao apareceu no XML de acessibilidade.
 echo Pode ser necessario conferir se o Flutter expoe esse cabecalho.
) else (
 echo [OK] O numero foi encontrado na arvore de acessibilidade.
)
echo.
echo Arquivo capturado: %TEMP%\bridge_pedido_tela.xml
echo Envie este XML para analise se a observacao nao abrir.
pause
exit /b 0
:fail
echo Erro no ADB. Confirme a depuracao e a conexao ao coletor.
pause
exit /b 1
