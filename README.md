# MLOG Scanner Bridge v2.7 — 1 clique por leitura

Base: v2.6. Nova regra: **um gatilho não pode gerar dois cliques**, nem enfileirar cliques para execução tardia.

## Diagnóstico analisado

No log de 22/10 10:41, o mesmo ciclo recebeu um clique por gatilho (`TRIGGER_NATIVE_CLICK_OK`) e depois outro ao processar o broadcast (`WAIT_EDITOR` seguido de novo clique). Leituras subsequentes aguardaram até ~3 s em fila. Ambas as situações motivaram esta correção.

## Comportamento

- Primeiro gatilho com MLOG reconhecido bloqueia o ciclo e tenta abrir o campo **uma única vez**.
- O primeiro broadcast do scanner preenche o campo. Retransmissões e bipes durante o processamento são **ignorados, nunca guardados**.
- Após a confirmação ser observada como mudança/fechamento do campo, o bloqueio é liberado.
- Se nenhuma leitura for decodificada, o gatilho é liberado após 1,35 s, sem toques atrasados.
- Se não for possível confirmar, o ciclo permanece bloqueado para evitar confirmação duplicada; usar **Diagnóstico → Rearmar após falha** somente depois de verificar o MLOG.
- O Bridge **não envia Enter**; o CMX continua responsável por isso.
- LOGO 1.svg no ícone de instalação; LOGO 2.svg no cabeçalho em duas cores para os temas.

⚠️ **Leituras acionadas enquanto ocupado serão descartadas**, não executadas mais tarde. O operador deve aguardar a conclusão antes de bipar novamente. Essa é uma escolha deliberada para evitar duplicações e cliques retardados; não representa suporte a bipagem ilimitada em alta frequência.

## Atualizar no Windows

```bat
cd /d "%USERPROFILE%\Downloads\MLOG-Scanner-Bridge"
powershell -NoProfile -Command "Expand-Archive -LiteralPath (Join-Path $env:USERPROFILE 'Downloads\MLOG_Scanner_Bridge_v27_Um_Clique_Por_Ciclo.zip') -DestinationPath . -Force"
git add -A
git commit -m "Bridge v27 trava de ciclo e logos"
git push origin main
```

## Instalar usando ADB

```bat
"%USERPROFILE%\Downloads\platform-tools\adb.exe" devices
"%USERPROFILE%\Downloads\platform-tools\adb.exe" install -r "%USERPROFILE%\Downloads\app-debug.apk"
```

## Segurança dos testes

Testar num único coletor, fora de pedidos reais, para confirmar: 1) um clique por gatilho; 2) o próximo clique só após confirmação; 3) Enter somente pelo CMX; 4) recuperação manual sem executar cliques atrasados. O código foi validado sintaticamente, mas o APK e a integração dependem da compilação e teste físico.
