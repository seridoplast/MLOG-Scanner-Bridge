# Análise da captura de 22/10/2026

A captura `Diagnostico_MLOG_20261022_080508.zip` contém eventos Android e vídeo. Nos eventos do Bridge foram identificados 17 ciclos:

- 17 `SCAN_START`
- 17 `BRIDGE_IME_ENTER_SENT`
- 9 `SCAN_FINISH_campo_mudou`
- 8 `SCAN_FINISH_direct_enter_nao_verificado`
- mediana de 574 ms por ciclo; as amostras incluíram 186–875 ms

A presença de `BRIDGE_IME_ENTER_SENT` **não** comprova que o aplicativo MLOG concluiu a operação. Alguns campos permaneciam preenchidos na última verificação. Isso pode ser latência do Flutter, carregamento de pedidos ou campo que conserva texto, e exige validação pelo contador do pedido.

O teclado próprio gera a tecla Enter antes de procurar uma tecla no Gboard. O ganho de tempo pode vir de antecipar o campo físico via gatilho e reduzir o agrupamento de eventos; **não há prova de que esses ajustes igualarão o Key Mapper**. Testes anteriores com Key Mapper e Bridge usaram velocidades de bipagem distintas e não permitem comparar diretamente os tempos de servidor.

A deduplicação de 140 ms foi preservada para impedir a regressão da v2.0 (broadcasts duplicados em 69–84 ms). Não foi removida em nome da velocidade.
