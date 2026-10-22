# MLOG Scanner Bridge v2.3 — Gatilho antecipado + Enter manual

**Versao experimental para testar primeiro em um unico CMX Android 14.** Base v2.2, preservando o teclado compacto QWERTY e o filtro de broadcasts repetidos de 140 ms.

## Alteracoes

1. **Pre-foco pelo gatilho ativado por padrao**, uma unica vez ao migrar da v2.2. Ao receber `KeyEvent.ACTION_DOWN` reconhecido, o Bridge tenta acionar o botao nativo de teclado do MLOG **antes** de receber o broadcast com o codigo. Devolve `false` para nao consumir a tecla nem reconfigurar o CMX.
2. **Aprender botao do coletor** em Ajustes, caso o codigo fisico nao seja um dos conhecidos: arme a aprendizagem, abra o MLOG e aperte o gatilho uma vez. Somente teclas nao textuais podem ser aprendidas. A primeira tecla apenas aprende; a segunda pode gerar o pre-foco.
3. **Diagnostico temporal sem codigos de barras nos logs**: `PHYSICAL_TRIGGER_SEEN_N`, `PREFocus_NATIVE_CLICK_OK`, `TRIGGER_TO_DECODE_MS_N`, `PREFocus_NATIVE_ICON_UNAVAILABLE`. Se nao houver `PHYSICAL_TRIGGER_SEEN`, o firmware pode nao entregar o evento de tecla ao Android; a melhoria de latencia nao funcionara por esse caminho, e o fluxo por broadcast continuara.
4. **Enter manual no teclado** no lugar de Tab, tanto no teclado numerico quanto QWERTY. O Enter automatico apos leitura foi preservado, separado da tecla manual.
5. **Mantidas protecoes**: filtro de broadcasts duplos, campo protegido de quantidade/senha, uma confirmacao por leitura, filas e cancelamento em navegacao. Nenhuma mudanca nas configuracoes do CMX.

## Instalar no GitHub via CMD (Windows)

Baixe o ZIP para Downloads:

```bat
cd /d "%USERPROFILE%\Downloads\MLOG-Scanner-Bridge"
powershell -NoProfile -Command "Expand-Archive -LiteralPath (Join-Path $env:USERPROFILE 'Downloads\MLOG_Scanner_Bridge_v23_PreFoco_Enter.zip') -DestinationPath . -Force"
git add -A
git commit -m "MLOG Bridge v23 pre-foco gatilho Enter manual"
git push origin main
```

Github Actions: baixar artefato `MLOG-Scanner-Bridge-v23-APK` de execucao verde.

## Instalar no CMX pelo ADB

Coloque `app-debug.apk` extraido em Downloads e conecte o coletor pelo cabo USB com depuracao autorizada.

```bat
"%USERPROFILE%\Downloads\platform-tools\adb.exe" devices
"%USERPROFILE%\Downloads\platform-tools\adb.exe" install -r "%USERPROFILE%\Downloads\app-debug.apk"
```

## Teste seguro de desempenho

- Com Key Mapper desativado, mantenha **Leitura assistida**, **teclado Bridge** e **Leitura rapida** como ja estavam funcionando.
- Em Ajustes, **Pre-foco pelo gatilho fisico** deve estar marcado; se estiver desmarcado, marque.
- Teste 10 leituras comuns e 20 rapidas em separacao de teste, inclusive o mesmo produto repetido. Confira no MLOG as quantidades efetivamente confirmadas e zero duplicacoes.
- Se nao houver melhora, capture os logs de gatilho abaixo. Se o firmware nao expor o evento de tecla, abra Ajustes > Aprender botao do coletor, volte ao MLOG e aperte o gatilho uma vez; depois teste novamente. Se nao for aprendido, nao force toques: mantenha o modo anterior.
- Em telas de informacoes ou cancelamento, o Bridge deve continuar sem acionar o teclado.

```bat
"%USERPROFILE%\Downloads\platform-tools\adb.exe" logcat -d -v time -s MLOGBridge:I > "%USERPROFILE%\Downloads\Bridge_v23_Log.txt"
```

**Rollback rapido:** desmarque **Pre-foco pelo gatilho fisico** em Ajustes para voltar ao fluxo da v2.2 sem reinstalar. Esta versao nao tem compilacao Android nem medicao no dispositivo confirmadas; apenas verificacao local de estrutura/sintaxe. O objetivo de igualar o Key Mapper ainda depende de o CMX fornecer o evento fisico e da resposta do MLOG.
