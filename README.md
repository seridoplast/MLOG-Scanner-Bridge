# MLOG Scanner Bridge v1.5 — reparo a partir do diagnóstico ADB de 21/10/2026

**Projeto de testes Android para o coletor CMX (Android 14, resolução 720 × 1440).** Não modifica o APK do MLOG nem altera as configurações do CMX. Mantém o Gboard e a interface monocromática.

## O que o novo diagnóstico demonstrou

Os alertas sobre códigos não pertencentes ao produto foram **ignorados**, conforme informado pelo operador.

- **16:16:08.641** — `EDITOR_READY_separacao`; **16:16:08.642** — `Flutter nao expos ACTION_SET_TEXT; codigo nao enviado` e `SCAN_FINISH_sem_set_text`. O Flutter havia exibido um editor, mas ainda não disponibilizava a ação de preenchimento. A versão anterior **descartava a leitura**.
- **16:16:13.908** — `SET_TEXT_OK`; **16:16:14.226** — `IME_ENTER_UNAVAILABLE`; **16:16:14.611** — `GBOARD_ENTER_NOT_FOUND`. O código foi inserido, mas o editor e o Gboard **não forneceram uma ação acessível de confirmação**.
- Em outras leituras, `CODE_CHANGED_AFTER_ENTER` indica apenas que o código saiu do campo; **não comprova aceitação do produto** pelo MLOG.

## Mudanças da v1.5

1. **Aguardar ação do Flutter**: mesmo quando o `EditText` é visto, se ainda não estiver focado ou não aceitar `ACTION_SET_TEXT`, o Bridge retém a leitura, observa os eventos e tenta de novo até o prazo do ciclo. Não encerra a operação no primeiro `ACTION_SET_TEXT` recusado.
2. **Enter do Gboard no dispositivo diagnosticado**: primeiro respeita o Enter enviado pelo CMX; depois tenta `ACTION_IME_ENTER`; depois procura a tecla de confirmação pelo nome e acessibilidade. **Somente se nenhuma dessas funcionar**, com código ainda no campo MLOG, foco confirmado e janela do **Gboard original** identificada, faz um gesto restrito à tecla ✓ na geometria observada (720 × 1440). Nunca toca na antiga posição do botão de teclado do MLOG nem na tecla zero. Essa última tentativa é **experimental e específica** deste coletor e do layout de Gboard documentado; se a geometria mudar, o gesto é bloqueado.
3. **Fila FIFO** de leituras consecutivas da v1.4 preservada; eventos temporizados registram a etapa sem gravar conteúdo dos códigos.
4. Sem configurações de scanner, sem internet, sem clipboard e sem novo teclado. Acessibilidade atua somente no MLOG.

**Não há como garantir que uma confirmação foi aceita apenas porque o Android registrou o gesto.** Confira o resultado na tela do MLOG. Teste fora da operação de pedidos até validar o comportamento, especialmente a confirmação ✓. A tentativa de toque no Gboard depende do layout e pode falhar, sem haver como forçar eventos privilegiados em outro aplicativo.

## Compilar pelo GitHub Actions usando CMD

Baixe `MLOG_Scanner_Bridge_v15_Diagnostico_Corrigido.zip` e coloque na pasta Downloads. Execute:

```bat
cd /d "%USERPROFILE%\Downloads\MLOG-Scanner-Bridge"
powershell -NoProfile -Command "Expand-Archive -LiteralPath (Join-Path $env:USERPROFILE 'Downloads\MLOG_Scanner_Bridge_v15_Diagnostico_Corrigido.zip') -DestinationPath . -Force"
git add -A
git commit -m "MLOG Bridge v15 correcao diagnostico"
git push origin main
```

Acesse https://github.com/seridoplast/MLOG-Scanner-Bridge/actions e baixe **MLOG-Scanner-Bridge-v15-APK** somente de uma execução **Success**. Extraia `app-debug.apk` na pasta Downloads.

## Instalar usando ADB / depuração USB

Conecte o coletor, desbloqueie a tela e autorize a depuração:

```bat
"%USERPROFILE%\Downloads\platform-tools\adb.exe" devices
"%USERPROFILE%\Downloads\platform-tools\adb.exe" install -r "%USERPROFILE%\Downloads\app-debug.apk"
```

O coletor precisa aparecer como `device` e a instalação terminar com `Success`. `unauthorized` exige autorizar a chave RSA no próprio coletor. A atualização com `-r` depende de assinatura de APK compatível.

## Teste de regressão

1. Com código **correto** de teste, bipar uma vez na separação com quantidade: botão nativo → campo focado → valor → confirmação. Não repetir se aparecer operação pendente; conferir no MLOG.
2. Repetir em separação sem quantidade. Bipar duas ou três vezes consecutivamente, observando se algum código fica no campo.
3. Validar uma leitura na tela de atividades e duas consultas sequenciais, sem tocar na barra manualmente.
4. Se ainda ocorrer erro, registre logo após a falha:

```bat
"%USERPROFILE%\Downloads\platform-tools\adb.exe" logcat -d -v time -s MLOGBridge:I > "%USERPROFILE%\Downloads\MLOG_Bridge_Diagnostico_v15.txt"
```

Atenção: o log desta versão **não registra códigos de barras**. Ao coletar outros logs (`logcat` completo, vídeo ou tela), revise informações de pedidos e usuários antes de compartilhar.

## Status da validação

O parser do Java 17, os arquivos XML e a fila FIFO foram testados localmente. **O APK Android completo e o comportamento do gesto no CMX não puderam ser executados neste ambiente**; a compilação final é pelo GitHub Actions, seguida do teste físico.
