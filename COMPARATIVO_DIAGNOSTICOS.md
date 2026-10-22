# Medição baseada na v2.6

Fonte: `Diagnostico_MLOG_20261022_104146.zip`, arquivo `eventos_android.txt`.

Foram registrados 16 `SCAN_FINISH` com os seguintes desfechos:
- 11 `campo_mudou`;
- 1 `campo_saiu`;
- 3 `sem_confirmacao_cmx`;
- 1 `tela_bloqueada`.

Para os **12** ciclos com mudança ou desaparecimento do campo, os valores `SCAN_ELAPSED_MS` foram: **764, 507, 207, 707, 900, 148, 717, 380, 361, 1051, 997, 178**.

**Média aritmética = 576,4 ms; mediana = 607 ms; máximo = 1051 ms.**

Adotamos um intervalo fixo de **585 ms**, ~9 ms acima da média, entre tentativas de clique no botão nativo do MLOG. Este intervalo é uma decisão de configuração, não uma garantia de que toda operação já terá sido confirmada. O usuário optou por desbloqueio temporizado em vez de uma trava indefinida condicionada ao Enter.

**Trade-off:** até ~1,33 tentativas de clique por segundo podem ser aceitas por essa janela. Gatilhos mais rápidos são ignorados pelo Bridge, não guardados. Isso prioriza evitar cliques duplos e travamentos, mas não alcança ainda a meta de ritmo obtida com Key Mapper.
