# v2.9.4 — plano mínimo de validação no coletor

1. No CMX: leitura e Enter automático ativados, sem Key Mapper para evitar interferência.
2. Com Bridge ativo, leia 10 vezes em ritmo espaçado (intervalos > 450 ms). Confira o número de registros no MLOG.
3. Faça 10 tentativas rápidas (< 450 ms): os toques extra não devem ser executados mais tarde; **não** espere que todas essas tentativas sejam registradas.
4. Após uma leitura que não confirmou, aguarde mais de 450 ms e faça outra tentativa: não deve exigir reiniciar ou rearmar.
5. Teste item comum, item com quantidade, bipagem de atividades e consulta.
6. Verifique o log: `CLICK_COOLDOWN_STARTED_450_MS`, `CLICK_COOLDOWN_IGNORED_REMAINING_MS`, `SECOND_NATIVE_CLICK_PREVENTED`.

## Nota

A referência é a **média de 12 ciclos observáveis de 576,4 ms**, não uma promessa de que 450 ms sempre seja suficiente para concluir a operação no MLOG. Os exemplos mais lentos superaram 1 segundo. Essa versão prioriza desbloqueio previsível e ausência de cliques pendentes; otimizações futuras para atingir ritmos maiores devem medir o fim da operação real, não somente antecipar toques.
