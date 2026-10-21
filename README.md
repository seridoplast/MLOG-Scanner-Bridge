# MLOG Scanner Bridge v2.1 — Leitura rapida sem duplicacao

Versao baseada nos diagnosticos enviados em 21/10/2026: `Diagnostico_NORMAL COM KEYMAPPER.zip` e `Diagnostico_V20.zip`.

## Causa da duplicacao

A v2.0 aceitava o mesmo codigo outra vez apos **65 ms** no modo rapido.
O log de teste mostra um segundo broadcast colocado na fila **69-84 ms** apos o primeiro, sem uma nova decodificacao fisica correspondente. Portanto, o mesmo bipe podia gerar **duas insercoes / duas confirmacoes**.

## Correcoes

- **Deduplicacao de broadcasts por codigo durante 140 ms**, inclusive com modo rapido ativado. O intervalo e medido na RECEPCAO do broadcast, nao no processamento da fila.
- **Tempos de Enter do modo rapido preservados** (55 ms de espera inicial): o problema nao se resolve tornando o Enter mais lento.
- Fila e teclado compacto preservados. **Sem mudanca nas configuracoes do CMX**.
- No `logcat`, `BROADCAST_ACCEPTED_CMX` / `BROADCAST_ACCEPTED_HIK` / `BROADCAST_ACCEPTED_GENERIC` e os equivalentes `BROADCAST_SUPPRESSED_*` indicam o que foi recebido/descartado sem expor os codigos.
- `ScanDuplicatePolicy.java` isolada, com testes Java independentes do Android.

### Limites

A janela de 140 ms foi escolhida com base nesta amostra: duplicatas em 69-84 ms; menor intervalo de duas leituras fisicas registradas no Key Mapper em 192 ms. Bipagens fisicas do **mesmo codigo com menos de 140 ms** podem ser descartadas e precisam de validacao. Em um scanner ideal a deduplicacao deveria usar identificador de evento de leitura do proprio motor, se disponivel.

**Importante:** o retorno `CODE_CHANGED_AFTER_ENTER` confirma apenas que o campo mudou, nao que o MLOG contabilizou corretamente o item. Validar a quantidade contabilizada usando itens de teste.

## Atualizar pelo CMD

Baixe `MLOG_Scanner_Bridge_v21_Sem_Duplicatas.zip` para Downloads e execute:

```bat
cd /d "%USERPROFILE%\Downloads\MLOG-Scanner-Bridge"
powershell -NoProfile -Command "Expand-Archive -LiteralPath (Join-Path $env:USERPROFILE 'Downloads\MLOG_Scanner_Bridge_v21_Sem_Duplicatas.zip') -DestinationPath . -Force"
git add -A
git commit -m "MLOG Bridge v21 filtro anti duplicacao"
git push origin main
```

GitHub Actions: artefato `MLOG-Scanner-Bridge-v21-APK`.

## Instalar pelo ADB

Coloque `app-debug.apk` em Downloads, com coletor conectado e autorizado:

```bat
"%USERPROFILE%\Downloads\platform-tools\adb.exe" devices
"%USERPROFILE%\Downloads\platform-tools\adb.exe" install -r "%USERPROFILE%\Downloads\app-debug.apk"
```

## Coletar eventos da leitura

```bat
"%USERPROFILE%\Downloads\platform-tools\adb.exe" logcat -d -v time -s MLOGBridge:I > "%USERPROFILE%\Downloads\Bridge_v21_Log.txt"
```

Conferir `BROADCAST_ACCEPTED` (uma por decodificacao), `BROADCAST_SUPPRESSED` (retransmissao), `SET_TEXT_OK` e `BRIDGE_IME_ENTER_SENT`. Testar com o Key Mapper desligado e o Enter do CMX desligado para evitar dupla confirmacao.

A compilacao completa depende do GitHub Actions e o funcionamento nao foi validado no coletor. Comece em um unico aparelho fora de pedidos reais.
