# v2.9.6 — validação

- Origem: projeto v2.9.5 (400 ms).
- Mudança de tempo: `TimedClickGate.COOLDOWN_MS = 200L`.
- Mudanças na IME: `BridgeScanInputMethodService` reinicia em modo compacto no MLOG após fechamento/mudança de campo; `Digitar` expande manualmente; `⌫ Apagar` funciona na barra compacta; após Enter manual recolhe o QWERTY.
- Ajustes: migração única (`BridgePreferences`) liga os seis controles padrão sem exigir reset de dados e respeita escolhas subsequentes. Habilitar a opção Bridge não substitui o teclado padrão escolhido no Android.
- Rotina de preenchimento do scanner e Enter CMX preservados.
- Verificação local: executar `javac` nos testes Java puros; sem Android SDK disponível para `assembleDebug` neste ambiente.
- Teste funcional: conferir recolhimento apenas no MLOG, seleção do IME no Android, botão Apagar e erro do Gboard na primeira leitura.
