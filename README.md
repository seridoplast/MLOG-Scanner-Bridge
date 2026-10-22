# MLOG Scanner Bridge v2.5 — retorno a v2.3 SEM Enter automatico

**Esta versao usa o codigo da v2.3 como base.** A v2.4 alterou o processamento e parou de abrir os campos; esta versao nao utiliza essa arquitetura.

## O que foi mantido da v2.3
- Captura dos broadcasts do CMX e insercao do codigo pelo Bridge (`ACTION_SET_TEXT`).
- Pre-foco ao apertar o gatilho (tecla fisica), detecao do botao nativo do MLOG, toque guiado e reabertura dos campos.
- Fila, filtro anti-retransmissao, suporte a separacao/quantidade/atividades/consulta/conferencia e interface QWERTY monocromatica.
- Teclado Bridge opcional, com botao **Enter manual**. Nao foram alteradas as configuracoes do CMX.

## Unica mudanca operacional
- **O Bridge nao envia Enter automaticamente.** Foram removidas as chamadas automáticas de `sendKeyEvent(KEYCODE_ENTER)`, `ACTION_IME_ENTER` e os cliques no Enter do Gboard.
- Depois de colocar o codigo no campo, observa passivamente ate ~1,1 segundo se o CMX limpou/mudou o campo. Nao gera confirmacao extra.
- O CMX precisa estar configurado para produzir o Enter e a forma de entrega deve ser compativel com a entrada em foco. **Se o CMX enviar Enter antes do Bridge abrir e preencher o campo, a confirmacao pode falhar — nenhum atraso ou envio alternativo pelo Bridge foi acrescentado.**

## Atualizar no CMD

Baixe o ZIP em Downloads e abra o CMD:

```bat
cd /d "%USERPROFILE%\Downloads\MLOG-Scanner-Bridge"
powershell -NoProfile -Command "Expand-Archive -LiteralPath (Join-Path $env:USERPROFILE 'Downloads\MLOG_Scanner_Bridge_v25_V23_Sem_Enter.zip') -DestinationPath . -Force"
git add -A
git commit -m "Bridge v25 base v23 somente remover Enter automatico"
git push origin main
```

## Gerar e instalar
- Abra a aba **Actions** no GitHub e baixe o artefato `MLOG-Scanner-Bridge-v25-APK` da execucao verde.
- Extraia `app-debug.apk` para Downloads.

```bat
"%USERPROFILE%\Downloads\platform-tools\adb.exe" devices
"%USERPROFILE%\Downloads\platform-tools\adb.exe" install -r "%USERPROFILE%\Downloads\app-debug.apk"
```

## Teste
- Configure o Enter automatico no CMX. Teste primeiro um campo simples fora do MLOG para assegurar que o CMX escreve e confirma.
- Faca **uma leitura** em uma tela de separacao de teste e verifique se o Bridge abriu o campo; veja se o Enter do CMX confirmou.
- Se aparecer `CMX_ENTER_NOT_OBSERVED`, o Bridge preencheu o campo, mas nao observou confirmacao do CMX. Volte a v2.3 se essa configuracao nao funcionar.

Este projeto passou por validacao sintatica dos arquivos, mas a compilacao completa e o uso no coletor devem ser verificados pelo GitHub e Android.
