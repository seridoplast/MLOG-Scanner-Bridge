# MLOG Scanner Bridge v1.6 — Resposta Rápida

Projeto de testes Android para coletor CMX com Android 14, integrado ao MLOG por acessibilidade e recepção passiva de broadcasts. **Não modifica as configurações do scanner original.** Não solicita internet e não persiste códigos de barras no disco.

## Base de diagnóstico — 21/10/2026 às 16:38–16:39

No arquivo `Diagnostico_MLOG_20261021_163737.zip`, ignorando as mensagens de códigos que não correspondem ao produto, foram identificadas sete leituras com `SET_TEXT_OK`, sete ocorrências de `IME_ENTER_UNAVAILABLE` e sete gestos `GBOARD_ACTION_GESTURE`. Não há registro de `QUEUE_FULL` ou `SCAN_QUEUED` nesse recorte.

Exemplo: às 16:38:50.481 o Bridge recebeu um código; às 16:38:50.629 ele estava inserido (148 ms); às 16:38:50.951 a tentativa de `ACTION_IME_ENTER` foi negada; só às 16:38:51.246 foi tentado o gesto no Gboard. A latência principal estava **depois da inserção**.

## Alterações

1. **Confirmação mais rápida:** depois de inserir o código e dar uma breve chance ao Enter do CMX, tenta `ACTION_IME_ENTER` do campo. Caso indisponível, passa à tentativa verificada de ✓ no Gboard na mesma checagem — elimina um ciclo de espera adicional. A primeira verificação começa após 155 ms (antes eram 300 ms); se a leitura já estiver no campo por wedge, 160 ms (antes 450 ms). Os tempos são tentativas, não metas garantidas.
2. **Sem limite total por sessão:** são permitidas quantas leituras consecutivas a operação suportar. A fila simultânea passou de **6 para 128 códigos pendentes** (mais o ativo), mantendo limite de memória para evitar travamento caso o MLOG pare de responder. Se chegar ao limite simultâneo, o app avisa e não lança códigos às cegas. Isso **não é um limite de 128 leituras totais**.
3. **Não reabrir teclado enquanto navega:** quando detecta clique em Informações, Voltar, Cancelar ou aviso/modal no MLOG, cancela as ações de clique/foco/Enter ainda pendentes e limpa a fila. A tela só volta a receber automação com um novo bipe quando o aviso não estiver aberto. Removida a rotina de preparação automática sem gatilho.
4. **Enter sem duplicidade:** uma vez acionado ✓, só observa a reação do Flutter por até aproximadamente 460 ms, sem clicar duas vezes em confirmar. Quando o campo some ou muda, encerra a leitura; isso não comprova aceitação do item, que depende do MLOG.
5. Mantida a interface monocromática, o ícone, a leitura passiva e o Gboard do Android. A v1.6 não envia configurações para o CMX.

## Como atualizar pelo CMD

Coloque `MLOG_Scanner_Bridge_v16_Resposta_Rapida.zip` em Downloads e execute:

```bat
cd /d "%USERPROFILE%\Downloads\MLOG-Scanner-Bridge"
powershell -NoProfile -Command "Expand-Archive -LiteralPath (Join-Path $env:USERPROFILE 'Downloads\MLOG_Scanner_Bridge_v16_Resposta_Rapida.zip') -DestinationPath . -Force"
git add -A
git commit -m "MLOG Bridge v16 confirmacao rapida e navegacao"
git push origin main
```

Acesse https://github.com/seridoplast/MLOG-Scanner-Bridge/actions e, após **Success**, baixe o artefato `MLOG-Scanner-Bridge-v16-APK`. Extraia `app-debug.apk` para Downloads.

## Instalar/atualizar via ADB

Conecte o coletor, desbloqueie a tela e autorize a depuração USB:

```bat
"%USERPROFILE%\Downloads\platform-tools\adb.exe" devices
"%USERPROFILE%\Downloads\platform-tools\adb.exe" install -r "%USERPROFILE%\Downloads\app-debug.apk"
```

O serial deve aparecer com `device` e a instalação terminar em `Success`. O `-r` depende de assinatura de teste compatível; caso o GitHub Actions produza assinatura diferente, **não desinstale automaticamente**, pois isso apagaria configurações. Considere uma assinatura de distribuição persistente para produção.

## Testes recomendados — coletor fora da operação

- Bipar um código correto na separação com quantidade e confirmar se primeira leitura é processada.
- Bipar 10–20 códigos sequenciais, observando se algum fica parado no campo ou dispara confirmação repetida.
- Abrir Informações e Cancelar enquanto o campo está aberto. O teclado **não deve ser reaberto sozinho** depois da navegação.
- Testar bipagem de atividades e duas consultas consecutivas.
- Se aparecer Enter duplo ou alguma quantidade errada, interrompa o teste e volte à versão anterior; não use com pedidos reais até validar.

Para extrair o diagnóstico temporizado após uma falha:

```bat
"%USERPROFILE%\Downloads\platform-tools\adb.exe" logcat -d -v time -s MLOGBridge:I > "%USERPROFILE%\Downloads\MLOG_Bridge_Diagnostico_v16.txt"
```

O log `MLOGBridge` não imprime o valor dos códigos. Logs completos e vídeos do MLOG podem mostrar dados operacionais, então revise antes de compartilhar.

## Validação do pacote

- Parser Java 17: **5 arquivos analisados, sem erros de sintaxe**.
- Testes da fila: FIFO, repetição, 10.000 leituras sequenciais, rajada de 128 pendentes e rejeição explícita na capacidade máxima: **aprovados**.
- XML: **7 arquivos válidos**.
- Compilação Android integral, assinatura e testes físicos do CMX: **pendentes**; devem ser realizados no GitHub Actions e coletor.
