# v3.0.4 — Testes e alterações

- Android `versionCode=41`, `versionName=3.0.4-reabrir-teclado-100ms`.
- Cooldown de leitura física: **100 ms** (`TimedClickGate`), validado em 27 verificações locais.
- Bipes virtuais: **900 ms mínimos** entre Enters e **180 ms** de espera após inserir o texto; os outros passos podem aumentar a duração.
- Teste de política: NÃO permitir envio se o teclado IME estiver invisível ou se a conexão de entrada do MLOG não estiver ativa. Verificações adicionais incluídas em `AutoRepeatPolicyTest`.
- Reabertura: uma ação de clique por repetição; aguarda teclado visível e conexão viva, com prazo de 6 s, sem fila de ações.
- `BarcodeCleanerTest`, `ScanQueueTest`, `ScanDuplicatePolicyTest`, `BarcodeFormatLabelTest`, `BarcodeArtifactReportTest`, `EnterReadinessPolicyTest`, `SeparatedQuantityProbeTest` e testes de tempo/repetição executados localmente.
- **Não compilado no Android SDK neste ambiente**. A compilação e o ensaio real no MLOG são essenciais para verificar se o terceiro bipe é aceito.
