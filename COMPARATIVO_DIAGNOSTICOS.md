# Ajuste da v2.9.6

A v2.6 registrou 12 ciclos com mudança/fechamento de campo, com média de aproximadamente 576 ms e mediana de 607 ms. Após experimentar 750, 585, 550, 500, 450 e 400 ms, o usuário solicitou **200 ms**.

O tempo agora define exclusivamente o bloqueio fixo entre tentativas de clique no botão nativo do MLOG (`TimedClickGate.COOLDOWN_MS`), e **não** o momento em que o CMX envia o Enter nem uma confirmação do processamento do Flutter.

A versão preserva as rotinas de leitura/broadcast e os diagnósticos existentes. Acrescenta recolhimento automático do QWERTY Bridge somente no MLOG, botão Apagar na barra compacta e seis ajustes ligados por padrão. A falha do Gboard na primeira leitura ainda carece de um log com exceção específica para atribuir causa com segurança.
