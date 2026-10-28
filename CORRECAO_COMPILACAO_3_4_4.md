# Correção da compilação v3.4.4

Na instância anônima de `BroadcastReceiver`, `this` refere-se ao receiver e não ao serviço Android. Troca para `MlogFocusAccessibilityService.this` no teste `ScanAssistState.isArmed(Context)`.

Erro corrigido: `incompatible types: <anonymous BroadcastReceiver> cannot be converted to Context`.

Ainda requer nova compilação completa no GitHub Actions.
