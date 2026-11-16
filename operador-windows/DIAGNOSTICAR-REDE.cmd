@echo off
setlocal
chcp 65001 >nul
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0DIAGNOSTICAR-REDE.ps1"
pause
