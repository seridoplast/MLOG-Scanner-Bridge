# MLOG Scanner Bridge v2.4 — foco apenas (experimental)

**Mudança de arquitetura solicitada:** o Bridge não recebe nem processa códigos no MLOG, não faz ACTION_SET_TEXT, não envia KEYCODE_ENTER, não mantém fila e não gerencia confirmação. O CMX Scanner deve fazer **leitura + digitação + Enter**.

## Funcionamento

- Gatilho físico (no diagnóstico: tecla 353) é detectado pelo serviço de acessibilidade, sem consumir o evento.
- Dentro de telas reconhecidas do MLOG, o Bridge clica no botão de teclado nativo ou foca a busca antes de o CMX terminar de decodificar.
- Se o editor já estiver focado, não executa outro clique.
- Gestos por posição só nas telas reconhecidas de separação e atividades e **somente com o teclado fechado**; nunca em quantidade, senha ou avisos. Desative "Toque guiado" se o firmware exibir controles em posições diferentes.
- Teclado Bridge, quando selecionado como IME do Android, fica com **teclas ocultas por padrão no MLOG**, exibindo apenas uma barra compacta com **Mostrar**. Um toque em Mostrar expande as teclas; fora do MLOG, o teclado manual funciona normalmente.
- CMX deve estar configurado para **emulação de teclado (keyboard wedge) com Enter**. O Bridge não trata os broadcasts na tela do MLOG. O teste passivo de broadcast da tela inicial continua disponível.

## Aviso essencial sobre compatibilidade

Se o CMX estiver emitindo código **somente por broadcast**, esta versão não inserirá código no MLOG e poderá parecer que o scanner não funciona. Antes de usar na separação, teste em um campo de texto simples se o CMX digita o código e envia Enter sozinho. Acessibilidade pode antecipar o foco, mas não altera o tempo que o MLOG Flutter leva para construir o campo.

Teste em um coletor fora da operação. Faça 10 leituras espaçadas, depois 20 rápidas, e compare com o pedido efetivamente confirmado. Se falhar, retorne ao APK v2.3 (assinatura compatível) ou faça rollback pelo histórico do repositório, sem forçar a desativação de proteções do Android.

## Compilação e instalação

Veja ATUALIZAR_PELO_CMD.txt. O build é executado no GitHub Actions, no fluxo `gerar-apk.yml`.

## Diagnóstico

`adb logcat -d -v time -s MLOGBridge:I`

Eventos relevantes: TRIGGER_SEEN_353, MLOG_KEYBOARD_CLICKED, SEARCH_FOCUS_REQUESTED, FOCUS_READY, GUIDED_NATIVE_BUTTON_TAP. Não há código ou conteúdo do campo nos registros.
