# MLOG Scanner Bridge v2.0 — Leitura Rapida (beta)

Esta versao **preserva o teclado compacto v1.9** e adiciona um modo de
leitura rapida *desligado por padrao*. O teclado experimental Bridge deve estar
selecionado no Android e a opcao **Usar teclado Bridge no Enter** deve estar
ativa. O modo nao altera a configuracao do CMX Scanner original.

## Por que a v1.9 acumulava leituras

A v1.9 esperava 235 ms para tentar o Enter depois de inserir o codigo e
poderia aguardar ate 440 ms pela verificacao de confirmacao. Durante esse
intervalo, outros codigos permaneciam em fila. Esses intervalos fixos tornam
visivel uma demora na separacao com bipagens continuas.

## Mudancas desta beta

1. Com `Leitura rapida (teste)` ativa **e teclado Bridge selecionado**, a
   primeira tentativa de confirmacao comeca depois de cerca de **55 ms** (em
   vez de 235 ms), **desde que o campo correto esteja focado e contenha o codigo**.
2. Eventos de alteracao/foco do MLOG iniciam verificacoes de confirmacao sem
   aguardar o proximo timer fixo. **Nao enviam Enter adicional**.
3. Quando a tela confirma a saida do codigo, a proxima leitura da fila
   comeca com intervalo menor (25 ms, antes 65 ms).
4. Janela de notificacoes da acessibilidade reduzida de 180 ms para
   50 ms **somente enquanto o modo rapido esta ativo**. A configuracao
   volta ao valor anterior quando o teste e desativado.
5. Repeticoes identicas sao aceitas apos 65 ms no modo rapido (140 ms no
   modo normal), evitando descartar bipagens legitimamente rapidas, mantendo
   protecao contra broadcasts duplicados quase simultaneos.
6. Sem limite total de scans por sessao, mas permanecem **128 leituras
   aguardando ao mesmo tempo**, por seguranca. Quando a fila encher, o
   diagnostico informa `QUEUE_FULL`: nao existe fila infinita confiavel.

## Seguranca operacional

- Enter enviado **apenas com editor MLOG validado**; nao tentar em campos de
  quantidade ou senha. Aguardos de confirmacao e bloqueios de navegacao da
  v1.9 foram preservados.
- O retorno de `sendKeyEvent` nao prova que o MLOG processou a leitura. A
  verificacao depende dos eventos e conteudo acessivel do Flutter.
- O modo rapido e opcional e experimental. Teste fora de pedidos reais.
- Nao usa internet, nao salva codigo em disco e nao altera configuracoes do
  leitor CMX.

## Como ativar e voltar

Abra Bridge > Ajustes: habilite `Leitura assistida`, `Usar teclado Bridge
no Enter` e `Leitura rapida (teste)`. O teclado Bridge precisa ser escolhido
como padrao no seletor do Android. Para voltar ao modo v1.9, **desmarque
apenas `Leitura rapida (teste)`**; nao precisa reinstalar.

## Publicar no GitHub (CMD do Windows)

Coloque `MLOG_Scanner_Bridge_v20_Turbo_Beta.zip` em Downloads:

```bat
cd /d "%USERPROFILE%\Downloads\MLOG-Scanner-Bridge"
git branch backup-v19-antes-turbo
powershell -NoProfile -Command "Expand-Archive -LiteralPath (Join-Path $env:USERPROFILE 'Downloads\MLOG_Scanner_Bridge_v20_Turbo_Beta.zip') -DestinationPath . -Force"
git add -A
git commit -m "Bridge v20 leitura rapida opcional"
git push origin main
```

Se `backup-v19-antes-turbo` ja existir, pule somente esse comando.
O GitHub Actions gerara `MLOG-Scanner-Bridge-v20-APK` quando a compilacao
passar. Extraia o `app-debug.apk` para Downloads.

## Instalar por USB (ADB)

```bat
"%USERPROFILE%\Downloads\platform-tools\adb.exe" devices
"%USERPROFILE%\Downloads\platform-tools\adb.exe" install -r "%USERPROFILE%\Downloads\app-debug.apk"
```

Se a assinatura `debug` mudar entre compilacoes, `install -r` pode falhar.
Nao desinstale as configuracoes de um coletor em operacao sem backup.

## Como avaliar a beta

Teste 10 leituras espassadas, depois 20 ou mais em sequencia, inclusive
repeticoes do mesmo produto. Confira no MLOG se todas foram **confirmadas uma
vez cada**, e nao apenas exibidas no campo. Se houver uma leitura nao
confirmada, pare e desative o modo rapido; envie `logcat`.

```bat
"%USERPROFILE%\Downloads\platform-tools\adb.exe" logcat -d -v time -s MLOGBridge:I > "%USERPROFILE%\Downloads\Bridge_v20_Log.txt"
```

Eventos relevantes: `SCAN_RECEIVED`, `SCAN_QUEUED`, `SET_TEXT_OK`,
`BRIDGE_IME_ENTER_SENT`, `CODE_CHANGED_AFTER_ENTER`, `SCAN_FINISH`,
`SCAN_ELAPSED_MS_...`, `QUEUE_FULL`. O log **nao inclui codigos**.

### Verificacao

Alteracoes revisadas, Java analisado sintaticamente, testes de fila/politica
executados. Ainda precisa compilar pelo GitHub e validar no coletor; nenhum
numero de desempenho real foi medido. A latencia final depende do MLOG e do
scanner.
