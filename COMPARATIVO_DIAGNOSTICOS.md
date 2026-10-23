# Base da alteração

Partindo da v3.0.4, que confirmou bipes automáticos funcionando no coletor:

- Nenhuma mudança nas rotinas de preenchimento, reabertura do IME ou envio de Enter N−1.
- Apenas desativação condicional da funcionalidade N−1 (`ScanAssistState.AUTO_BIPS_ENABLED = false`) e remoção da gaveta da interface flutuante enquanto desativada.
- Janela de clique da leitura física passa a ser definida pela preferência local `click_cooldown_ms`, com valor inicial de 100 ms.
- O diagnóstico `CLICK_COOLDOWN_STARTED_MS_X` passa a reportar o intervalo configurado em cada gatilho, em vez de um texto fixo antigo.
