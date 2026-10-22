# Diferenças 3.0.0 → 3.0.1

- Bug relatado: após 1º físico e 1º virtual, o contador mostrava 'Enter sem confirmação visível, parei em 2/N'.
- Causa: `observeAutoCountEnter()` exigia mudança visível no Flutter dentro de 2500 ms.
- Ajuste: remover essa exigência para repetições virtuais; após `SET_TEXT` com sucesso e um Enter enviado, incrementa `autoCountDone` e agenda próximo envio respeitando 155 ms mínimos.
- Campo que fica com o mesmo código: limpa antes de reinserir para forçar mudança de texto.
- Não ocorre envio de Enter automático nas leituras normais; o CMX continua responsável.
- Acompanhamento: `AUTO_COUNT_ENTER_DISPATCHED_N` representa envio, não registro confirmado. Nenhum código é incluído nos logs.
- Teste de aceitação obrigatório: 3, depois 10 envios em ambiente de teste; comparar resultado real no MLOG. Em caso de erro, enviar diagnóstico para identificar falha do Flutter.
