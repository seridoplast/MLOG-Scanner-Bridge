# MLOG Scanner Bridge v2.2 — QWERTY, Tab e resposta otimizada

**Beta para teste em um coletor.** Baseada no código da v2.1 e no arquivo `Diagnostico_MLOG_20261022_080508.zip`.

## Diagnóstico que motivou esta versão

- 17 ciclos de leitura no log do Bridge.
- Tempo mediano do ciclo do Bridge: **574 ms** (não equivale ao tempo total de processamento do MLOG).
- 9 ciclos terminaram com `CODE_CHANGED_AFTER_ENTER`; isso indica que o campo mudou, **não prova que o pedido foi contabilizado**.
- 8 ciclos terminaram em `BRIDGE_IME_ENTER_UNVERIFIED`, com o código ainda aparente no campo na última verificação.
- O Enter do teclado próprio foi enviado nos 17 ciclos; portanto, reduzir apenas a espera inicial não resolve todos os atrasos. Parte depende da atualização de tela e/ou do processamento pelo MLOG.

## Alterações da v2.2

1. **Teclado manual QWERTY**: QWERTYUIOP / ASDFGHJKL / Shift + ZXCVBNM + apagar. A fileira de números em cima das letras foi retirada. O modo numérico permanece disponível em `?123` ou para campos numéricos.
2. **Tab manual** no lugar do ✓: em formulários, tenta `IME_ACTION_NEXT` quando indicado; caso contrário envia `KEYCODE_TAB`, com recuperação para `IME_ACTION_NEXT` se o teclado não aceitar a tecla. Destinado a passar de usuário para senha. A confirmação **automática do scanner permanece `KEYCODE_ENTER`** e não virou Tab.
3. **Eventos do MLOG menos agrupados no modo rápido**: throttle 50 → 15 ms; reação aos eventos de campo/Enter antecipada; primeira checagem de Enter de 55 → 30 ms. Essas são configurações do código, não uma medição de latência real.
4. **Pré-foco pelo gatilho físico (EXPERIMENTAL e DESLIGADO)**: se o Android entregar um evento de tecla física comum (F1–F12, botões industriais, câmera), o Bridge pode acionar o botão de teclado do MLOG **antes de chegar o broadcast com o código**. Isso pode aproximar o momento de abertura do campo ao Key Mapper. O Bridge devolve `false` para não consumir o gatilho. Não funcionará caso o serviço CMX capture a tecla antes do Android; pode também disputar o filtro de eventos com outro serviço de acessibilidade. Teste com o Key Mapper desativado.
5. **Medição adicional**: logs `QUEUE_WAIT_MS_*` e `ENTER_AFTER_FILL_MS_*`, sem revelar conteúdo dos códigos.
6. Mantida a proteção de **140 ms contra broadcasts repetidos** da mesma leitura; a fila não tem limite total de itens ao longo da sessão, mas comporta no máximo 128 aguardando ao mesmo tempo.

## Como instalar a versão do projeto pelo CMD

Coloque `MLOG_Scanner_Bridge_v22_QWERTY_Tab_Resposta.zip` em Downloads:

```bat
cd /d "%USERPROFILE%\Downloads\MLOG-Scanner-Bridge"
git branch backup-v21-antes-v22
powershell -NoProfile -Command "Expand-Archive -LiteralPath (Join-Path $env:USERPROFILE 'Downloads\MLOG_Scanner_Bridge_v22_QWERTY_Tab_Resposta.zip') -DestinationPath . -Force"
git add -A
git commit -m "Bridge v22 QWERTY Tab resposta rapida"
git push origin main
```

GitHub Actions: arquivo **MLOG-Scanner-Bridge-v22-APK**, após a compilação ficar verde.

## Instalar pelo ADB (Windows)

Coloque `app-debug.apk` extraído do artefato em Downloads e conecte o coletor por USB com depuração autorizada:

```bat
"%USERPROFILE%\Downloads\platform-tools\adb.exe" devices
"%USERPROFILE%\Downloads\platform-tools\adb.exe" install -r "%USERPROFILE%\Downloads\app-debug.apk"
```

## Teste recomendado

1. Com **Leitura rápida** e teclado Bridge selecionados, faça 10 leituras espaçadas e depois 20 rápidas, em uma atividade de teste. Conte produtos efetivamente registrados pelo MLOG, não apenas os bipes.
2. Faça leitura repetida do mesmo produto físico e confirme que cada bipagem gera no máximo um registro. A janela de 140 ms pode descartar duas bipagens legítimas do mesmo código extremamente próximas.
3. No login, abra as teclas manualmente, digite usuário, pressione **Tab ↹** e confirme que o cursor vai para senha. Caso o MLOG não ofereça foco seguinte via Android, o Tab poderá não avançar; relatar o comportamento.
4. Somente depois, teste **Pré-foco pelo gatilho físico** em **um** coletor, sem Key Mapper. Verifique se o campo abre no aperto do gatilho; se não, desative a opção. O serviço não deve ficar abrindo campos sem disparo.
5. Se houver Enter não verificado, confira se o pedido foi registrado antes de repetir a leitura.

## Diagnóstico por USB

```bat
"%USERPROFILE%\Downloads\platform-tools\adb.exe" logcat -d -v time -s MLOGBridge:I > "%USERPROFILE%\Downloads\Bridge_v22_Log.txt"
```

As métricas de `SCAN_ELAPSED_MS`, `QUEUE_WAIT_MS`, `ENTER_AFTER_FILL_MS` ajudam a distinguir espera interna de processamento do MLOG. O Bridge não controla as consultas à rede/servidor do MLOG.

## Segurança e limitações

Aplicativo não solicita permissão de internet, não armazena conteúdo das leituras em arquivo e usa acessibilidade limitada ao MLOG. O pré-foco exige a capacidade de filtrar eventos de tecla (autorizada explicitamente pelo Android via serviço de acessibilidade) e fica desligado por padrão. Não é uma garantia de funcionar com todas as versões de firmware CMX. Não desative permanentemente Play Protect. **A compilação Android e o teste real no coletor ainda estão pendentes.**
