# v3.1.9 — Uma verificacao do codigo antes do Enter

- Mantem o agendamento de Enter pelo intervalo de ms da v3.1.8.
- Imediatamente antes do Enter, relê o editor focado e compara texto inteiro com o código do broadcast nativo.
- Se diferente, faz **uma** tentativa de ACTION_SET_TEXT para reescrever o código integral e checa novamente **35 ms depois**.
- Se continuar parcial, o Enter não é enviado. O aplicativo avisa que aquela leitura não foi confirmada.
- **Não há espera de confirmação visual após Enter, nem conferência de fila pendente.**
- Sem restrição universal a 8 ou 13 dígitos: há códigos internos com comprimentos variáveis.
- Não consegue identificar um código já truncado na origem do broadcast se o scanner não informar formato/tamanho esperado.
- `ACTION_SET_TEXT` pode não refletir o campo imediatamente: por isso a checagem antes de Enter.
- Limitação: a comparação prova apenas que o texto integral estava no editor naquele instante, **não** que o MLOG registrou o produto.

## Teste no coletor

1. Faça backup da versao operacional; use um pedido de testes.
2. Atualize pelo GitHub Actions / ADB mantendo a mesma assinatura.
3. Escaneie um EAN-13, um codigo interno curto valido e uma sequencia rapida.
4. Se aparecer `Codigo incompleto no campo`, confira o campo e repita o bipe; **nenhum Enter deve ter sido enviado para aquele ciclo**.
5. Em Logcat, procure apenas eventos sem dados de produto: `PRE_ENTER_CODE_EXACT_MATCH`, `PRE_ENTER_PARTIAL_REWRITE_ONCE`, `PRE_ENTER_INCOMPLETE_BLOCKED`.
