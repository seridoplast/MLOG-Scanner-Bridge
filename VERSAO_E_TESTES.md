# v2.9.7 — 150 ms / Formato lido

- Base: v2.9.6.
- Alteracao no bloqueio: `TimedClickGate.COOLDOWN_MS` de 200L para 150L.
- ScannerProtocol.type() recupera informacoes de simbologia no broadcast do CMX/Hikvision e utiliza BarcodeFormatLabel.normalize().
- Tela principal de testes mostra `Formato lido: ...` como linha propria.
- Quando metadados ausentes, indica `Nao informado pelo CMX` (nao supoe pelo comprimento).
- Testes locais: `TimedClickGateTest`, `BarcodeFormatLabelTest`, `ScanDuplicatePolicyTest`, `ScanQueueTest`, `EnterReadinessPolicyTest`.
- Compilacao completa no GitHub Actions e teste com coletor fisico pendentes.
