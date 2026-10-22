# Validação v3.0.2

- `SeparatedQuantityProbeTest`: incrementos em `PC SEPARADO`, troca de produto e rejeição de KG.
- `AutoRepeatPolicyTest`: cálculo N−1 e mínimo de 155 ms.
- `TimedClickGateTest`: bloqueio físico de 150 ms.
- `ScanQueueTest` e `ScanDuplicatePolicyTest`: fila e eco, comportamento normal preservado.
- `BarcodeCleanerTest`, `BarcodeArtifactReportTest`, `BarcodeFormatLabelTest`: tratamento/formato preservados.
- `javac` analisou as fontes, mas sem Android SDK as dependências Android não podem ser compiladas; nenhum APK foi produzido localmente.

A verificação do incremento `SEPARADO` é baseada na árvore de acessibilidade do Flutter; validar no coletor. Para telas sem esse contador, o Bridge continuará mostrando **tentativas não confirmadas**.
