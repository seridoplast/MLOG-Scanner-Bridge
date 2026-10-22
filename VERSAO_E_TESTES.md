# v2.7 — resumo técnico

## Evidências do diagnóstico recebido

- `TRIGGER_NATIVE_CLICK_OK_separacao` seguido de `WAIT_EDITOR_separacao` e novo clique pelo broadcast no mesmo ciclo, gerando interações repetidas.
- `QUEUE_WAIT_MS_1358`, `_2207`, `_3030`: leituras posteriores estavam sendo processadas tarde demais.
- `CMX_ENTER_NOT_OBSERVED`: não há garantia de confirmação em todos os casos.

## Mitigações

- `ScanCycleGate`: estados `IDLE`, `TRIGGERED`, `RECEIVED`, `WAIT_CONFIRMATION`, `NEEDS_ATTENTION`.
- `claimClick()` garante um único clique/gesto por ciclo; ação física no gatilho tem prioridade.
- Sem processamento tardio de toques ou enfileiramento de códigos. Leituras durante ocupação são descartadas; não são reexecutadas.
- Bloqueio liberado após mudança ou fechamento do campo observados no MLOG. Esses sinais são aproximadores de conclusão visual, não confirmação de servidor.
- Falhas sem confirmação permanecem bloqueadas, com rearmamento explícito nas configurações de diagnóstico.
- O Bridge não gera Enter automático. CMX controla a confirmação.
- Logos do usuário preservadas em `branding/`, processadas em PNGs Android. A logo 2 fica preta no claro e branca no escuro, com azul preservado.

## Limitações

Acessibilidade Flutter pode oferecer ações e estados em momentos diferentes do processamento do MLOG; não existe confirmação de servidor observável a partir desses eventos. A nova versão prioriza segurança contra cliques duplicados em vez de throughput ilimitado. O CMX pode bipar mesmo quando o Bridge descarta a leitura — teste fora da operação real antes de implantar.

## Verificações locais

Sintaxe Java, teste de máquina de estados, XML e integridade do ZIP. Compilação Android completa e homologação no coletor pendentes.
