# Validação v3.0.3

- Base v3.0.2, `versionCode` 40 e `versionName` 3.0.3-enter-lento-check-verde.
- `AutoRepeatPolicyTest`: mínimo de 900 ms entre Enters e N−1 repetições; 28 verificações.
- `TimedClickGateTest`: bloqueio físico de 150 ms; 27 verificações.
- `SeparatedQuantityProbeTest`: reconhecimento de incremento da contagem do mesmo produto.
- `ScanQueueTest`, `ScanDuplicatePolicyTest`, `EnterReadinessPolicyTest` e testes de formatos/limpeza executados e aprovados.
- MLOG não é controlado diretamente pelo Bridge: um par de teclas Enter emitido **não comprova que um item foi separado/gravado**.
- O cartão de permissões tem estados individuais, atualizados ao retornar ao app e uma vez por segundo; desaparece quando todas as quatro verificações estão satisfeitas.
- Não foi compilado APK Android no ambiente atual. Compile no GitHub Actions e valide num pedido de teste.
