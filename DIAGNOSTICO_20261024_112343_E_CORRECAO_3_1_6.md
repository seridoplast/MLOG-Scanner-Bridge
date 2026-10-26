# Diagnostico 24/10/2026 e alteracoes v3.1.6 (experimental)

## Evidencias

- `SCAN_START`: 13; `BROADCAST_ACCEPTED_DIRECT`: 38 durante log parcial.
- `SCAN_FINISH_campo_mudou`: 8; `SCAN_FINISH_sem_confirmacao_cmx`: 5.
- Os ciclos sem confirmacao demoram ~1,5-1,7 s. Em tres deles a fila foi interrompida.
- O Enter por `InputConnection.sendKeyEvent(DOWN/UP)` confirmou algumas leituras. O fallback de acessibilidade muitas vezes nao foi seguido por mudanca do campo.
- Nao existe comprovacao de que o MLOG gravou toda a quantidade apenas pelos logs: campo alterado nao equivale a commit persistido.

## Mudancas

- No modo nativo, prazo minimo para primeira tentativa de Enter: 60 ms depois do `SET_TEXT_OK` (antes 180 ms).
- Priorizar Enter DOWN/UP atraves do teclado Bridge ao campo explicitamente validado, mesmo quando a *vista* visual do teclado esta escondida, desde que a InputConnection seja do MLOG.
- Fallback unico no campo de acessibilidade, depois InputMethod accessibility. Nenhum segundo Enter automatico no mesmo ciclo.
- Reduzir a espera de recuperação de foco no modo nativo de 90 para 45 ms.
- Manter liberacao de fila apenas por mudanca observada na UI; nunca afirmar confirmacao com base no retorno do Enter.
- Aumentar limite explicito de espera no modo nativo de 8 para 24 bipes, com aviso da profundidade da fila. Quando ha falha nao verificada, a fila e cancelada para evitar contagem silenciosamente incorreta.
- Mantidas assinaturas/rotas nativas e nome do pacote.

## Como testar com seguranca

1. Salve o APK anterior; instale usando `adb install -r` e o APK assinado pela mesma chave.
2. Em pedido de teste, compare bipe unico, 3 bipes com intervalo de ~300 ms e 5 bipes consecutivos do mesmo produto.
3. Confira QUANTIDADE GRAVADA no MLOG, nao so mensagens ou desaparicao do campo.
4. Se houver atraso, repeticao ou contagem incorreta, pare e restaure a versao anterior.
5. Capture novamente `CAPTURAR_LOG_BRIDGE.cmd` e identifique os eventos `NATIVE_ENTER_VERIFIED_IME_SENT`, `SCAN_FINISH`, `NATIVE_SCAN_QUEUED_DEPTH`.

Nao foi possivel compilar nem executar no aparelho neste ambiente. Os testes unitarios puros nao sao prova da interacao Android/Flutter.
