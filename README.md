# MLOG Scanner Bridge v3.0.4 — Reconectar teclado entre bipes

Base **v3.0.3**, mantendo painel flutuante, tratador de códigos, checks verdes, botão Apagar, tema, logs, e Enter físico pelo CMX.

## Mudanças

- **Leitura física**: cooldown de **100 ms** (`TimedClickGate.COOLDOWN_MS = 100L`), desbloqueio pelo relógio e sem cliques acumulados.
- **Bipes automáticos**: cadência conservadora de **900 ms entre Enters virtuais**; o primeiro bipe é físico e os demais são virtuais (quantidade N-1).
- **Bug do terceiro bipe**: a v3.0.3 podia reconhecer um `EditText` focado depois de o MLOG ter encerrado a janela de teclado. Agora o Bridge aguarda o teclado visível e uma conexão IME viva do MLOG; se estiver fechado, reabre pelo botão nativo ou, em último caso, pelo próprio campo correto antes de preencher/enviar Enter.
- Um clique de reabertura por repetição; **nunca guarda cliques para executar depois**. Prazo de 6 segundos por repetição para tolerar o processamento Flutter.
- Se a conexão fechar após inserir o código, tenta recuperá-la antes de enviar o Enter do item pendente.
- Registros no diagnóstico identificam reabertura, reconexão e tentativa de Enter sem supor que o pedido tenha sido gravado.

**Atenção:** 100 ms é inferior ao tempo médio de conclusão que medimos anteriormente; a leitura física pode duplicar cliques ou perder leituras se o MLOG estiver lento. No modo automático, **900 ms é intervalo mínimo**, não garantia de aceitação do Flutter. Teste inicialmente 3 bipes em um pedido de teste e confira o total realmente separado; depois experimente 10.

## Como atualizar

Baixe `MLOG_Scanner_Bridge_v304_Reabrir_IME_100ms.zip` em Downloads, abra o Prompt de Comando:

```bat
cd /d "%USERPROFILE%\Downloads\MLOG-Scanner-Bridge"
powershell -NoProfile -Command "Expand-Archive -LiteralPath (Join-Path $env:USERPROFILE 'Downloads\MLOG_Scanner_Bridge_v304_Reabrir_IME_100ms.zip') -DestinationPath . -Force"
git add -A
git commit -m "Bridge v304 corrigir terceiro bipe e 100ms"
git push origin main && del /q "%USERPROFILE%\Downloads\MLOG_Scanner_Bridge_v304_Reabrir_IME_100ms.zip"
```

No GitHub Actions, baixe o artefato `MLOG-Scanner-Bridge-v304-APK`. Instale via ADB:

```bat
"%USERPROFILE%\Downloads\platform-tools\adb.exe" devices
"%USERPROFILE%\Downloads\platform-tools\adb.exe" install -r "%USERPROFILE%\Downloads\app-debug.apk"
```

**Validação:** testes Java puros passaram; projeto ainda precisa ser compilado no GitHub Actions e validado no CMX Android 14. Não há garantia de gravação efetiva apenas pelo Enter enviado.
