# Diagnóstico dirigido da v1.6

A diferença de tempo entre `SET_TEXT_OK` e `GBOARD_ACTION_GESTURE` na v1.5 indicou atraso majoritariamente na confirmação, não na captura do código.

| Etapa | Exemplo 16:38:50 | Tempo desde SCAN_RECEIVED |
|---|---|---:|
| SCAN_RECEIVED | 16:38:50.481 | 0 ms |
| SET_TEXT_OK | 16:38:50.629 | 148 ms |
| IME_ENTER_UNAVAILABLE | 16:38:50.951 | 470 ms |
| GBOARD_ACTION_GESTURE | 16:38:51.246 | 765 ms |
| SCAN_FINISH_campo_saiu | 16:38:51.654 | 1173 ms |

O registro `campo_saiu` **não prova aceitação do produto**, apenas mudança/remoção do editor. As mensagens de código incompatível foram ignoradas, conforme a orientação do operador.

As alterações desta versão são experimentais e devem ser testadas antes do uso operacional.
