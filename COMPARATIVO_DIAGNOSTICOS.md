# Comparativo dos diagnósticos — 21/10/2026

| Medida observada no log | Key Mapper (referência) | Bridge v2.0 (modo rápido) |
|---|---:|---:|
| Decodificações físicas registradas pelo serviço PDA | 14 | 9 |
| Janela entre primeira e última decodificação | 6,878 s | 17,226 s |
| Menor intervalo entre decodificações físicas | 192 ms | 1.040 ms |
| Chamadas `SET_TEXT_OK` pelo Bridge | Não se aplica | 20 |
| Tentativas `BRIDGE_IME_ENTER_SENT` | Não se aplica | 20 |
| Leituras do Bridge recebidas + enfileiradas visíveis | 0 | 19 (9 recebidas + 10 enfileiradas) |
| Segundo broadcast enfileirado após uma leitura | Não se aplica | 69-84 ms em 9 pares examinados |
| Tempo por ciclo do Bridge (mediana `SCAN_START` a `SCAN_FINISH`) | Sem marcador correspondente | 327 ms (19 ciclos completos visíveis) |

**Cuidado com a comparação:** os dois testes não foram executados com o mesmo ritmo de operador, itens ou duração ativa; os valores são *observados*, não um benchmark controlado. O Key Mapper não emite as mesmas marcações de confirmação. A comparação mais importante é que a v2.0 aceitou 19 eventos do Bridge embora o serviço físico tenha registrado só 9 decodificações no trecho, com uma segunda entrada 69-84 ms após a primeira.

**Causa provável verificável no código:** `ScanCoordinator.accept(code, fast)` reduziu a janela para 65 ms em modo rápido. Essa janela aceita os ecos observados de 69-84 ms. Na referência, o menor intervalo observado de duas decodificações físicas foi 192 ms; por isso a v2.1 usa 140 ms.

**O que não dá para provar apenas com o log:** a contagem de itens efetivamente confirmados no backend do MLOG, se cada segunda transmissão é do mesmo action ou de actions diferentes, ou o melhor tempo possível sob fluxo real. A v2.1 registra somente aceitos/descartados para próxima validação.

No log v2.1, o sufixo `_CMX`, `_HIK` ou `_GENERIC` informa **somente a action** que transmitiu o evento, sem salvar o codigo. Esse dado deve permitir decidir se os ecos vem de dois canais diferentes ou da retransmissao de um mesmo canal.
