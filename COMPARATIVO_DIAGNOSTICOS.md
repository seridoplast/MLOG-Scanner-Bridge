# Ajuste da v2.9.7

A v2.6 registrou 12 ciclos com mudança/fechamento de campo, com média de aproximadamente 576 ms e mediana de 607 ms. Após experimentar 750, 585, 550, 500, 450, 400 e 200 ms, o usuário solicitou **150 ms**.

O tempo agora define exclusivamente o bloqueio fixo entre tentativas de clique no botão nativo do MLOG (`TimedClickGate.COOLDOWN_MS`), e **não** o momento em que o CMX envia o Enter nem uma confirmação do processamento do Flutter.

A versão preserva as rotinas de foco, Enter, teclado compacto, botão Apagar e preferências da v2.9.6, alterando apenas o cooldown e a apresentação do formato recebido do scanner na tela de teste. Se o CMX não enviar a simbologia nos metadados do broadcast, a interface informa a ausência do dado, sem inferir pelo comprimento. A falha do Gboard na primeira leitura ainda carece de log conclusivo.
