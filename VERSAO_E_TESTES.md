# v2.9.8 — validação e riscos

- Base v2.9.7, cooldown normal **150 ms** inalterado.
- Nova permissão `SYSTEM_ALERT_WINDOW`, solicitada ao usuário para a bolha opcional; sem rede e sem armazenamento de códigos em histórico.
- Tratamento de códigos via `BarcodeCleaner.clean` na recepção broadcast; sem deduzir simbologia.
- Contador explícito 1–100, intervalo mínimo **155 ms**, condicionado à observação de conclusão; CMX faz a primeira leitura, Bridge IME faz Enter somente nas repetições.
- Não há envio de comandos para iniciar/parar/configurar o CMX.
- Indicadores de sucesso dependem dos sinais da árvore de acessibilidade; eventos visuais não garantem persistência no MLOG.
- Verificar permissão e persistência da bolha no Android 14; validar InputConnection e segurança de quantidade/peso antes de produção.
- Verificar logs `AUTO_COUNT_*`, `SCAN_CLEANED_REMOVED_CHARS_*` sem conteúdo de códigos.
- Todos os testes Java/parse/XML locais passaram. O Gradle/Android SDK não estavam presentes, portanto não há APK compilado/localmente testado.
