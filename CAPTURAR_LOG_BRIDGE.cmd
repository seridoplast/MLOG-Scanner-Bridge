@echo off
setlocal
set "ADB=%USERPROFILE%\Downloads\platform-tools\adb.exe"
if not exist "%ADB%" (
  echo ADB nao encontrado em "%ADB%".
  pause
  exit /b 1
)
"%ADB%" devices
set "OUT=%USERPROFILE%\Downloads\MLOG_Bridge_Diagnostico.txt"
"%ADB%" logcat -d -v time -s MLOGBridge:I > "%OUT%"
if errorlevel 1 (
  echo Falha ao coletar log. Confira a autorizacao USB.
) else (
  echo Arquivo criado: "%OUT%"
  echo O log registra as etapas do fluxo, mas nao os codigos bipados.
)
pause
