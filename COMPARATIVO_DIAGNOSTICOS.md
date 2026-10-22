# Diagnóstico da v3.0.3 — por que o terceiro bipe não entra

Fonte: `Diagnostico_MLOG_20261022_204355.zip`, eventos Android de 22/10/2026 às 20:44.

| Evento | Horário local | Evidência |
|---|---|---|
| Primeiro bipe físico | 20:44:34 | `AUTO_FIRST_SEPARADO_CONFIRMED` |
| Segundo bipe (virtual) | 20:44:35.147–35.622 | `AUTO_COUNT_NATIVE_CLICK_OK`, `AUTO_COUNT_ENTER_DISPATCHED_2`, `AUTO_COUNT_SEPARADO_CONFIRMED_2` |
| Teclado anterior desaparece | 20:44:36.328–36.340 | Surface `InputMethod` destruída |
| Terceiro bipe (virtual) | 20:44:36.361–36.585 | `AUTO_COUNT_SET_TEXT_OK` e `AUTO_COUNT_ENTER_DISPATCHED_3`, mas **sem novo clique nativo** |
| Terceiro sem confirmação | 20:44:37.487 | `AUTO_COUNT_UNVERIFIED_CONTINUING_3` |

**Hipótese técnica forte:** depois de confirmar o segundo item, o Flutter encerra o IME, mas mantém o editor de texto focado. O Bridge antigo verificava o foco do editor e o tempo desde a abertura, porém **não verificava se a janela de teclado e sua InputConnection estavam realmente ativas**. Assim, o terceiro código podia aparecer em um editor que não enviaria o Enter ao Flutter.

## Correções experimentais da v3.0.4

1. Antes de cada repetição, exigir janela IME visível **e** InputConnection ativa do teclado Bridge na tela correta do MLOG.
2. Se o teclado estiver fechado, tentar **um** clique nativo no ícone de teclado do MLOG. Se o ícone não estiver visível, tentar **um** clique no editor reconhecido, sem tocar em quantidade ou outro comando.
3. Aguardar a nova sessão do teclado, com limite por repetição de 6 segundos; não ficar em loop de cliques.
4. Se a conexão sumir entre inserir o texto e enviar Enter, voltar ao passo de reabertura, **sem confirmar esse item ainda**.
5. Logar `AUTO_IME_HIDDEN_REOPEN_N`, `AUTO_NATIVE_REOPEN_CLICK_N`, `AUTO_EDITOR_REOPEN_CLICK_N`, `AUTO_IME_READY_N` e `AUTO_IME_LOST_BEFORE_ENTER_N` para entender o terceiro bipe no CMX.
6. Mudar exclusivamente o cooldown da leitura física de 150 ms para **100 ms**. Manter intervalo conservador de **900 ms entre Enters virtuais**.

**Limitações:** enviar um Enter não comprova gravação no MLOG. Até testar no coletor, não é possível afirmar que o terceiro bipe passará. O lote não deve ser usado em pedidos reais sem verificar a contagem `SEPARADO` e os registros gravados no pedido.
