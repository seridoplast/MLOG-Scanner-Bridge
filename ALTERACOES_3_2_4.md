# MLOG Scanner Bridge v3.2.4 — gatilho e latência

- Gatilho contínuo (opt-in): inicia após 1.000 ms; pulsa no bloqueio de ms configurado, com piso técnico de 40 ms para não saturar o serviço Android. Continua interrompendo ao soltar, com limite de 30 s. O firmware pode ignorar solicitações durante uma captura.
- Modo nativo: evita varredura de modal, de produto e de quantidade em cada Enter. Continua exigindo foco, campo editável e igualdade exata do texto com o código recebido; uma reparação breve se necessário.
- Novo bipe na mensagem específica de "código de barras/produto não encontrado": clica apenas em OK (se exposto como ação de acessibilidade). Esse bipe é consumido e não conta produto. Não fecha outros avisos ou diálogos.
- Removida varredura de tela periódica de diagnóstico durante ocioso e buscas pré-foco por tecla no modo nativo.
- A deduplicação dos resultados do hardware passa a seguir o intervalo configurado, com piso de 35 ms para ecos do mesmo broadcast, substituindo a janela fixa de 140 ms do código anterior.

**Limites:** o firmware do leitor pode entregar um resultado a cada ~500 ms mesmo recebendo comandos a cada 100 ms. Um Enter enviado não prova que o MLOG gravou a quantidade. A identificação do diálogo depende de o Flutter expor o texto e o botão OK na acessibilidade. Em falha, feche manualmente; nunca faça toque por coordenadas para OK de forma indiscriminada.

**Validação:** testes Java independentes são executáveis via `javac`; compilação Android e homologação em coletor real ainda necessárias.
