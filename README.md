# MLOG Scanner Bridge v2.6 — Botao nativo a cada gatilho

Esta versao parte **diretamente da v2.5**, sem reconstruir o processamento de codigos.

## Alteracao principal
- Ao pressionar o gatilho fisico, ANTES da decodificacao, o Bridge identifica a tela do MLOG e procura o botao nativo de teclado. Quando a entrada ainda nao estiver aberta, executa `ACTION_CLICK` (ou um gesto no proprio icone quando possivel).
- O gatilho do coletor observado nos diagnosticos tem o codigo **353**; foi incluido explicitamente. Eventos de tecla desconhecidos continuam nao acionando gestos.
- O clique nao depende de a fila anterior ter terminado, nem confunde teclado Android visivel com o campo Flutter preparado.
- Se o campo correto **ja estiver aberto e focado**, nao clica outra vez porque o botao do MLOG pode ser um alternador e fechar o campo. A entrada fica aberta, como solicitado.
- Para **separacao (com e sem quantidade)** e **atividades**, pode usar um toque relativo ao layout conhecido quando nao ha elemento clicavel identificado, desde que o ponto nao esteja coberto pelo teclado Android. Isso permite que a barra compacta do Bridge fique aberta sem impedir os toques.
- Em **consulta e conferencia de venda**, a caixa de busca ja existe e o gatilho so recupera o foco.
- Preservada a insercao de codigo do Bridge, fila e deduplicacao; **nao envia Enter automatico**. Enter automatico continua a cargo do CMX.
- Nao envia comandos de configuracao ao CMX nem altera a escolha do teclado.

## Limite tecnico
O CMX pode transmitir o Enter antes do campo Flutter aceitar a leitura. Esta versao antecipa o clique, mas nao consegue garantir que o Enter do CMX seja sincronizado com o preenchimento realizado depois. Se ainda for necessario bipar duas vezes, sera preciso ajustar a entrega/confirmacao e medir pelo log, nao clicar repetidamente.

## CMD — atualizar o projeto no GitHub

Baixe o ZIP em Downloads e execute:

```bat
cd /d "%USERPROFILE%\Downloads\MLOG-Scanner-Bridge"
powershell -NoProfile -Command "Expand-Archive -LiteralPath (Join-Path $env:USERPROFILE 'Downloads\MLOG_Scanner_Bridge_v26_Botao_A_Cada_Gatilho.zip') -DestinationPath . -Force"
git add -A
git commit -m "MLOG Bridge v26 botao nativo por gatilho"
git push origin main
```

Abra https://github.com/seridoplast/MLOG-Scanner-Bridge/actions e baixe `MLOG-Scanner-Bridge-v26-APK` quando a compilacao estiver verde.

## CMD — instalar por depuracao USB

Extrai o `app-debug.apk` para Downloads:

```bat
"%USERPROFILE%\Downloads\platform-tools\adb.exe" devices
"%USERPROFILE%\Downloads\platform-tools\adb.exe" install -r "%USERPROFILE%\Downloads\app-debug.apk"
```

## Teste seguro
1. Mantenha `Pre-foco pelo gatilho` e `Leitura assistida` ativados. Configure o Enter automatico no CMX.
2. Teste primeiro em um pedido ficticio: um item comum, outro com quantidade e a tela de atividades.
3. Com o campo fechado, aperte o gatilho uma vez: o Bridge deve clicar no botao nativo do MLOG. Com o campo ja aberto, nao deve fechar nem alternar o teclado.
4. Teste bipagens consecutivas e cheque uma leitura por bipe; confirme que o MLOG processou cada Enter.
5. Para comparar o momento do gatilho e do codigo:

```bat
"%USERPROFILE%\Downloads\platform-tools\adb.exe" logcat -d -v time -s MLOGBridge:I > "%USERPROFILE%\Downloads\Bridge_v26_Log.txt"
```

Registros esperados: `PHYSICAL_TRIGGER_SEEN_353`, `TRIGGER_OPEN_ATTEMPT_...`, `TRIGGER_NATIVE_CLICK_OK_...` (ou `TRIGGER_GESTURE_SENT_...`), `TRIGGER_EDITOR_ALREADY_OPEN`, `TRIGGER_TO_DECODE_MS_...`.

## Estado de verificacao
Revisao estatica de Java/XML e testes das classes puras podem ser executados aqui. A compilacao Android e a validacao com o MLOG no coletor dependem do GitHub Actions e do hardware real.
