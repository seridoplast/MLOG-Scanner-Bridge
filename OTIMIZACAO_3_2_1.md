# MLOG Scanner Bridge 3.2.1 — ciclo otimizado (experimental)

Base: 3.2.0, sem alterar o protocolo nativo de decodificacao Hikrobot, sem voltar a aguardar confirmacao visual de quantidade e sem ativar o gatilho longo por padrao.

## Alteracoes de desempenho

1. **Temporizador assíncrono de Enter:** `Handler.createAsync(Looper.getMainLooper())` nos Android 9+; evita aguardar barreiras sincrônicas de desenho de tela. As ações de acessibilidade continuam na thread principal. O agendador registra `FIXED_ENTER_TIMER_LAG_MS_...` para separar atraso do Android do intervalo escolhido.
2. **Validacao sem nova busca completa, quando seguro:** reutiliza o `AccessibilityNodeInfo` que acabou de aceitar `ACTION_SET_TEXT`, somente dentro do ciclo atual, após `refresh()`, janela correspondente e teste de código exato. Volta para `inspect()` se a referência não estiver válida.
3. **Reuso de foco entre bipes:** só usa o campo anterior se ainda estiver **focado, vazio, editável, visível e na mesma janela**; dura entre 20 e 2000 ms. Se qualquer requisito falhar, mantém o caminho normal de mapeamento.
4. **Deteccao de avisos com uma passagem na árvore:** elimina uma segunda visita que ocorria em `isBlockingOverlay`.
5. **Eventos do Flutter:** reduz a varredura de aviso a cada evento `TYPE_WINDOW_CONTENT_CHANGED` (no máximo uma a cada 90 ms), mas checa imediatamente em mudança de janela e novamente antes do Enter.
6. **Menos E/S:** a inserção normal e o crescimento de fila no modo nativo não gravam a mensagem de status em preferências a cada bipe. Alertas continuam sendo comunicados.
7. **Mantidas as protecoes:** código completo no editor antes do Enter, bloqueio de modal e campo incorreto, proteção de produto anterior e uma tentativa de Enter por ciclo.

## Limites e testes

- A configuração de 100 ms é um **intervalo mínimo**, e NÃO um prazo garantido para todo o ciclo Android/Flutter. O sistema pode levar mais tempo para expor o editor, entregar o `ACTION_SET_TEXT` e processar o Enter.
- `sendKeyEvent` ou `ACTION_IME_ENTER` aceitos não provam que o pedido foi gravado no MLOG. Esta versão continua usando confirmação temporizada, como solicitado.
- Foi executada a suíte Java de lógica, inclusive `NativeFocusReusePolicyTest`; compilação APK e homologação em coletor CMX real continuam pendentes.
- Testar **somente em pedido de teste** antes de implantação em produção: uma leitura, cinco leituras consecutivas, troca de produto, campo parcialmente preenchido, modal de erro e tela de separação. Confirmar quantidade efetivamente registrada.
- Para comparar desempenho, capturar novo diagnóstico e procurar `NATIVE_FOCUS_REUSED_EMPTY_EDITOR`, `FIXED_ENTER_EDITOR_FAST_PATH`, `FIXED_ENTER_EDITOR_FULL_LOOKUP`, `FIXED_ENTER_TIMER_LAG_MS_...` e `SCAN_ELAPSED_MS_...`.
- Se houver leitura incorreta, retornar ao APK assinado 3.2.0 pelo mesmo projeto/build (com versionCode superior) ou desativar o modo nativo. Não desinstalar sem preservar os dados.

## Publicacao

O ZIP contém fonte para GitHub Actions. **Não contém APK compilado.** O mesmo `applicationId` e a assinatura fixa corporativa foram mantidos. O `versionCode` de CI usa `GITHUB_RUN_NUMBER` e segue a numeração do repositório.
