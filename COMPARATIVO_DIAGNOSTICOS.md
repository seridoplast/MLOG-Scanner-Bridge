# Diagnóstico 20:06:27 de 22/10/2026

- Primeiro bipe do lote armado às 20:07:06.617.
- `SCAN_FINISH_campo_mudou` às 20:07:06.981, 364 ms após o início.
- Repetições `_2` a `_10` registradas como `AUTO_COUNT_ENTER_DISPATCHED` entre 20:07:07.109 e 20:07:09.295.
- Muitos envios foram classificados como `AUTO_COUNT_ENTER_BRIDGE_IME`. Não há comprovação no log de que cada chamada tenha causado `onSubmitted` no Flutter ou registro persistido.
- Capturas do vídeo mostram variações da contagem `SEPARADO` durante a operação, o que aponta para processamento parcial/assíncrono.
- Correção v3.0.2: distinguir tentativas de incrementos reais; aguardar aumento visível `SEPARADO` quando disponível, usar ação do editor como prioridade, interromper em vez de declarar 10/10 se a contagem não corresponder.
