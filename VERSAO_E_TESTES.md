# Versão 3.0.5

- Base preservada: v3.0.4, incluindo leitura normal, abertura do teclado e Enter do CMX.
- `ScanAssistState.AUTO_BIPS_ENABLED = false` por escolha do operador. Motor e testes mantidos para reativação futura; gaveta protegida por feature flag. Lotes persistidos são desarmados.
- `TimedClickGate` recebe um tempo de bloqueio configurável pelo operador, default 100 ms e limites 80 a 2000 ms. O prazo de um ciclo já aberto não muda retroativamente.
- Em Ajustes → Avançado o botão Aplicar salva o valor e atualiza imediatamente o serviço de acessibilidade; Restaurar padrão volta aos 100 ms.
- `TRIGGER_BOUNCE_MS = 70 ms` e deduplicação de broadcasts não foram alterados.
- O APK ainda depende de compilação em Android Gradle Plugin/GitHub Actions e validação no CMX.
