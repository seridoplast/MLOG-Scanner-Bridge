@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Process -FilePath powershell.exe -Verb RunAs -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File','""%~dp0CONFIGURAR-FIREWALL-PRIVADO.ps1""')"
if errorlevel 1 echo Nao foi possivel abrir o instalador de regras. Execute o arquivo .ps1 como administrador.
pause
