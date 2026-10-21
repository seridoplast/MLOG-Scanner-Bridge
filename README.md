# MLOG Scanner Bridge v1.7 — Correção da regressão de Enter

Esta versão é baseada na v1.6, com o diagnóstico `Diagnostico_MLOG_20261021_165942.zip`.

## Causa observada

A v1.6 inseria o código (`SET_TEXT_OK`) mas procurava o Enter do Gboard cedo demais:
`IME_ENTER_UNAVAILABLE` seguido, em ~2 ms, por `GBOARD_ENTER_NOT_FOUND`. Então encerrava a leitura sem confirmar.
Na v1.5 o Gboard era acionado mais tarde, com `GBOARD_ACTION_GESTURE` e `GBOARD_GESTURE_COMPLETED`.

## Correção

- Primeira verificação após 235 ms do preenchimento.
- Se o Gboard ainda não oferecer a tecla de confirmação, repetir a busca a cada 85 ms, até 1.650 ms desde a inserção.
- Não marcar a tecla como acionada até existir ação ou gesto aceito.
- Interromper a tentativa se o campo mudar, perder a segurança, surgir modal ou a leitura for cancelada.
- Nunca tocar no botão de quantidade ou em tecla numérica arbitrária.
- Manter fila de 128 leituras aguardando, sem limite de total na sessão, e sem reconfigurar o CMX.
- Logcat: `GBOARD_KEY_NOT_READY_RETRY`, `GBOARD_ACTION_GESTURE`, `GBOARD_GESTURE_COMPLETED`, `GBOARD_KEY_TIMEOUT`.

A verificação do fim da leitura é baseada no estado do campo; mudança de tela não prova aceitação do produto.

## Atualização por CMD

```bat
cd /d "%USERPROFILE%\Downloads\MLOG-Scanner-Bridge"
powershell -NoProfile -Command "Expand-Archive -LiteralPath (Join-Path $env:USERPROFILE 'Downloads\MLOG_Scanner_Bridge_v17_Enter_Resiliente.zip') -DestinationPath . -Force"
git add -A
git commit -m "MLOG Bridge v17 espera adaptativa do Enter"
git push origin main
```

Baixar artefato **MLOG-Scanner-Bridge-v17-APK** no GitHub Actions e extrair `app-debug.apk` em Downloads.

## Instalação ADB

```bat
"%USERPROFILE%\Downloads\platform-tools\adb.exe" devices
"%USERPROFILE%\Downloads\platform-tools\adb.exe" install -r "%USERPROFILE%\Downloads\app-debug.apk"
```

## Diagnóstico sem códigos completos

```bat
"%USERPROFILE%\Downloads\platform-tools\adb.exe" logcat -d -v time -s MLOGBridge:I > "%USERPROFILE%\Downloads\MLOG_Bridge_v17_Log.txt"
```

## Validação

Testes de sintaxe Java, testes da política de espera e fila, XML e ZIP. Compilação Android completa e teste no CMX não feitos neste ambiente. Testar fora de pedidos operacionais.
