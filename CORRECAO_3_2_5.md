# v3.2.5 — Protecao de codigo na troca de produto

Comparacao com APK v2.9.7: a versao antiga dependia do CMXScanner para enviar Enter, logo a sua latencia nao corresponde ao ciclo completo do Bridge atual.

Diagnostico 26/10 15:46: 44 ciclos finalizados, mediana ~113,5 ms, maximo 217 ms. 14 eventos PRE_ENTER_PARTIAL_REWRITE_ONCE e 37 buscas completas do editor. O scanner nativo entregou os codigos; o log nao permite concluir que cada codigo foi aceito pelo MLOG.

Mudanca conservadora: o reparo de ACTION_SET_TEXT antes do Enter ocorre somente se o campo contiver um prefixo nao vazio do codigo da leitura atual. Campo com outro codigo, um sufixo ou texto de outra tela agora bloqueia o Enter e registra PRE_ENTER_FOREIGN_TEXT_BLOCKED, em vez de sobrescrever silenciosamente. Mantidos temporizador, leitor nativo, modo de gatilho, atualizador e verificacao de codigo completo.

**Importante**: a mudanca reduz o risco de enviar codigo errado durante troca de produto. Nao garante reduzir latencia nem evita que o scanner leia uma etiqueta valida mas de outro produto. O modo v2.9.7 era mais rapido em parte porque nao enviava o Enter. Teste em pedido de treinamento e confira as quantidades. Nao desinstale a versao atual; mantenha o APK assinado para rollback.
