# MLOG Scanner Bridge v2.8 — observar Enter e destravar sem cliques antigos

## Base

Derivado da v2.7 com as duas logos fornecidas e a regra de **um clique por ciclo**. CMX continua responsavel por decodificar e enviar Enter. O Bridge **nao envia Enter automaticamente** e **nao guarda cliques para depois**.

## Evidencia do diagnostico 22/10/2026 11:22–11:24

- `CMX_ENTER_PASSIVE_WAIT` / `CMX_ENTER_NOT_OBSERVED` depois de 1.100 ms.
- `GATE_LOCKED_UNCONFIRMED` colocava o ciclo em `NEEDS_ATTENTION` permanentemente.
- 11 gatilhos registrados como `TRIGGER_IGNORED_LOCKED_*` durante a captura.
- Outras leituras apresentaram `CMX_FIELD_CHANGED` e `GATE_RELEASE_CONFIRMED` em 101–232 ms.

## Alteracoes

1. **Deteccao de Enter real, quando acessivel**: observa `KEYCODE_ENTER` / `KEYCODE_NUMPAD_ENTER` por `AccessibilityService.onKeyEvent()` e `InputMethodService.onKeyUp()`; retorna `false`/`super` e nao consome/reenvia o Enter.
2. **Liberacao apos Enter observado**: se Enter chegou apos codigo inserido e a janela nao estiver em modal, libera o ciclo apos curta verificacao (90 ms). Isso observa que a tecla foi recebida no Android, mas nao garante resposta do servidor MLOG.
3. **Confirmacao tardia por UI**: quando 1.100 ms expiram sem confirmacao verificavel, continua observando por ate 8 segundos a mudanca de tela, desaparicao do campo ou alteracao do conteudo. Sem alteracao, **nao** desbloqueia apenas por tempo.
4. **Novo gatilho reavalia o estado** antes de recusar: se a tela ja mudou e o evento de acessibilidade atrasou, libera e permite iniciar o proximo ciclo. Nunca executa clique antigo em fila.
5. **Protecao contra falsos positivos**: outro campo editavel como Quantidade nao comprova conclusao. Modais e login continuam bloqueados.
6. **Diagnostico**: `RAW_ENTER_KEY_OBSERVED_accessibility`, `RAW_ENTER_KEY_OBSERVED_bridge_ime`, `GATE_RELEASE_RAW_ENTER`, `LATE_CONFIRMATION_OBSERVED_*`, `GATE_RELEASE_LATE_UI_CHANGE`, `LATE_CONFIRMATION_NOT_VISIBLE`.

### Limitacao importante

Se o CMX enviar Enter sintetico diretamente ao MLOG por um mecanismo que nao publica um KeyEvent, o Bridge **nao consegue interceptar a tecla** com garantia. Nesse caso, detecta o resultado visivel no Flutter. Se o Flutter nao expuser nem Enter nem mudanca de campo, mantem o bloqueio por seguranca e oferece o rearme manual apos conferencia. Nunca confundir `Enter enviado` com `item confirmado no servidor`.

## Compilar no GitHub

No Windows (ZIP salvo em Downloads):

```cmd
cd /d "%USERPROFILE%\Downloads\MLOG-Scanner-Bridge"
powershell -NoProfile -Command "Expand-Archive -LiteralPath (Join-Path $env:USERPROFILE 'Downloads\MLOG_Scanner_Bridge_v28_Enter_Observado.zip') -DestinationPath . -Force"
git add -A
git commit -m "Bridge v28 desbloqueio por Enter e confirmacao tardia"
git push origin main
```

Em `Actions`, baixar o artefato `MLOG-Scanner-Bridge-v28-APK` gerado com sucesso. O APK nao vem pronto neste ZIP.

## Instalar via ADB

Extrair `app-debug.apk` para Downloads:

```cmd
"%USERPROFILE%\Downloads\platform-tools\adb.exe" devices
"%USERPROFILE%\Downloads\platform-tools\adb.exe" install -r "%USERPROFILE%\Downloads\app-debug.apk"
```

## Capturar o novo diagnostico

```cmd
"%USERPROFILE%\Downloads\platform-tools\adb.exe" logcat -d -v time -s MLOGBridge:I > "%USERPROFILE%\Downloads\Bridge_v28_Log.txt"
```

## Validacao local

- Parser Java para os 10 fontes: OK
- `ScanCycleGateTest`: OK
- `ConfirmationReleasePolicyTest`: OK
- XMLs validos, ZIP integro: OK
- **Compilacao Android, teste no coletor e Enter sintetico CMX: nao validados neste ambiente**

Teste primeiro em um coletor fora de pedidos reais, comparando cinco leituras lentas com uma sequencia de dez. Conferir que nao houve lancamentos duplicados. O Bridge pode rejeitar um gatilho quando a operacao anterior ainda nao terminou; a correcao evita bloqueio persistente quando uma confirmacao tardia e visivel.
