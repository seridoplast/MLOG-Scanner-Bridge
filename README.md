# MLOG Scanner Bridge v1.8 — Enter direto via AccessibilityInputConnection

## O que mudou

O CMX permanece como leitor físico. O Bridge passa a tentar confirmar a leitura via API do Android 14 `InputMethod.AccessibilityInputConnection.sendKeyEvent()` — par `KEYCODE_ENTER` ACTION_DOWN/ACTION_UP enviado diretamente ao campo com foco.

- Somente quando MLOG estiver em primeiro plano, a tela for reconhecida, o editor correto estiver visível e focado, e o código completo já estiver no campo.
- Não cria teclado próprio nem altera configurações do CMX.
- Não usa clipboard, permissões de internet ou coleta de senhas.
- Verifica pacote de destino na `EditorInfo` antes de enviar.
- Se o Android ainda não expuser InputConnection, aguarda até 440 ms e recorre ao método anterior do Gboard. **Se enviar o Enter direto, não envia um segundo Enter por fallback.**
- Não pressupõe sucesso: registra `DIRECT_ENTER_SENT` e observa se a tela reagiu. Os avisos de código errado continuam sendo validação normal do MLOG.

## IMPORTANTE — evitar Enter duplicado

A proposta é deixar **CMX somente capturar códigos** e **Bridge responsável pelo Enter**. Para testes, desligue manualmente a opção de Enter automático **no aplicativo CMX Scanner**, se possível. O Bridge **não fará essa mudança sozinho**. Se ambos enviarem Enter, podem ocorrer confirmações duplicadas.

## Enviar para o GitHub pelo CMD

```bat
cd /d "%USERPROFILE%\Downloads\MLOG-Scanner-Bridge"
powershell -NoProfile -Command "Expand-Archive -LiteralPath (Join-Path $env:USERPROFILE 'Downloads\MLOG_Scanner_Bridge_v18_Enter_Direto.zip') -DestinationPath . -Force"
git add -A
git commit -m "MLOG Bridge v18 Enter direto Android 14"
git push origin main
```

GitHub Actions: `MLOG-Scanner-Bridge-v18-APK`. Extraia `app-debug.apk` para Downloads.

## Instalar pelo ADB

```bat
"%USERPROFILE%\Downloads\platform-tools\adb.exe" devices
"%USERPROFILE%\Downloads\platform-tools\adb.exe" install -r "%USERPROFILE%\Downloads\app-debug.apk"
```

O coletor precisa aparecer como `device`; resultado de instalação esperado: `Success`. Acessibilidade já concedida normalmente permanece se a assinatura do APK continuar igual.

## Diagnosticar

```bat
"%USERPROFILE%\Downloads\platform-tools\adb.exe" logcat -d -v time -s MLOGBridge:I > "%USERPROFILE%\Downloads\Bridge_v18_Log.txt"
```

Eventos: `INPUT_CONNECTION_NOT_READY`, `INPUT_CONNECTION_UNAVAILABLE_FALLBACK`, `DIRECT_ENTER_SENT`, `DIRECT_ENTER_SENT_UNVERIFIED`, `CODE_CHANGED_AFTER_ENTER`, `GBOARD_ACTION_GESTURE`.

## Limitações

Esta é uma implementação experimental baseada na API oficial Android API 33+. O Flutter pode não expor `InputConnection` estável em todos os campos. O retorno `sendKeyEvent()` é `void`, não é confirmação de processamento. Testar primeiro em operação de demonstração, nunca em pedidos reais antes da validação.
