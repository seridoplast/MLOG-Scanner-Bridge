# MLOG Scanner Bridge v3.0.5 — Tratador + Avançado

Atualização a partir da **v3.0.4** (IME reaberto por repetição, leitura física 100 ms). Esta versão **mantém intacta a leitura física com Enter do CMX** e não altera o motor de repetição bem-sucedido; apenas o desabilita temporariamente.

## Mudanças

1. **Bipes automáticos temporariamente desativados e ocultos:** a interface flutuante exibe só o **Tratador de códigos**. Nenhum lote anterior é restaurado. Todo o motor N−1 e os testes permanecem preservados em `MlogFocusAccessibilityService.java`, `AutoRepeatPolicy.java`, `FloatingToolsService.java` e `tests/`.
2. **Como reativar no futuro:** em `ScanAssistState.java`, alterar `AUTO_BIPS_ENABLED = false` para `true`, revisar e testar o aplicativo e recompilar. A seção da gaveta do painel flutuante está guardada dentro de `if(ScanAssistState.AUTO_BIPS_ENABLED)`.
3. **Ajustes → Avançado:** o tempo de bloqueio de clique entre gatilhos físicos agora é configurável, de **80 a 2000 ms**. O padrão é **100 ms**. Informar o número, tocar em **Aplicar tempo**, ou usar **Restaurar padrão (100 ms)**.
4. O ajuste é persistido no aparelho na preferência `click_cooldown_ms` e enviado imediatamente ao serviço de acessibilidade em execução, sem necessidade de reiniciar nem gerar novo APK. Em inicialização posterior, o serviço restaura o tempo salvo.
5. O tempo rege somente a janela de cliques do gatilho (`TimedClickGate`). **Não altera** deduplicação de broadcasts, debounce do gatilho ou Enter do CMX.

**Cuidado:** reduzir demais o tempo pode permitir outro clique antes de o Flutter concluir a leitura anterior. Testar inicialmente fora dos pedidos reais.

## Atualizar pelo CMD (Windows)

Coloque `MLOG_Scanner_Bridge_v305_Avancado_Tratador.zip` em Downloads e execute:

```bat
cd /d "%USERPROFILE%\Downloads\MLOG-Scanner-Bridge"
powershell -NoProfile -Command "Expand-Archive -LiteralPath (Join-Path $env:USERPROFILE 'Downloads\MLOG_Scanner_Bridge_v305_Avancado_Tratador.zip') -DestinationPath . -Force"
git add -A
git commit -m "Bridge v305 avancado ajustar ms e bipes ocultos"
git push origin main && del /q "%USERPROFILE%\Downloads\MLOG_Scanner_Bridge_v305_Avancado_Tratador.zip"
```

No GitHub Actions, baixar o artefato `MLOG-Scanner-Bridge-v305-APK`. O ZIP somente é excluído após um `git push` bem-sucedido.

## Instalar o APK pelo ADB

```bat
"%USERPROFILE%\Downloads\platform-tools\adb.exe" devices
"%USERPROFILE%\Downloads\platform-tools\adb.exe" install -r "%USERPROFILE%\Downloads\app-debug.apk"
```

## Validação

Os testes locais Java do controle temporal, inclusive alteração dinâmica do intervalo e verificação dos limites, passaram. Sintaxe dos Java/XML e integridade do ZIP conferidas. **O APK precisa ser compilado no GitHub Actions e testado no coletor Android 14.**
