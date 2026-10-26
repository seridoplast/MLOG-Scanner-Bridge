# v3.1.8 — Enter pelo intervalo de ms (modo nativo)

- Configuracao **Tempo do ciclo (ms)**: o mesmo intervalo controla admissao de leituras (TimedClickGate) e a primeira tentativa de Enter após inserir cada codigo.
- A sequencia e: receber broadcast nativo -> abrir/focar editor -> ACTION_SET_TEXT -> agendar Enter para >= inicio do ciclo + intervalo e >= ultimo Enter + intervalo -> enviar UMA vez -> iniciar proxima leitura.
- Nao observa a alteracao visual do campo nem o incremento de quantidade **apos** o Enter.
- Remove o cartao de conciliacao manual de bipes pendentes. A fila interna e transitória, limitada, e segue assim que o Enter é tentado.
- Mantem verificacoes ANTES de digitar ou enviar Enter para evitar agir sobre dialogs, senhas ou outros aplicativos.
- O Enter enviado nao prova registro no MLOG. Se o teclado IME e a ação de acessibilidade falharem, o Bridge reporta falha; nao reenvia automaticamente para evitar duplicacao.
- Se o Flutter demorar a exibir o campo, o tempo de abertura soma à duração; nao ha como garantir ciclo exato com aplicativos separados.
- Sem teste fisico nesta revisao; valide em um pedido de treinamento.

- No modo nativo, abertura do editor tem janela curta de ate 1,2 s; se nao abrir, nao gera Enter e libera o proximo ciclo com aviso.
