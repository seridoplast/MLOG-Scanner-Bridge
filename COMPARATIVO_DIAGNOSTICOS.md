# Continuidade v2.9.7 → v2.9.8

- Preservado `TimedClickGate.COOLDOWN_MS = 150L`, formato exibido no teste, foco por gatilho, teclado compacto e Enter pelo CMX no modo normal.
- O tratamento é opt-in; sem ativação, a entrada e o processamento do código seguem a v2.9.7.
- O contador é opt-in e inclui um fluxo de repetição separado, sem mudar a lógica padrão de leitura simples.
- Intervalo mínimo entre inserções = **155 ms**, sujeito à conclusão/atualização do Flutter; não garante 155 ms efetivos.
- Falha e cancelamento interrompem o contador, sem tarefas antigas executadas posteriormente.
