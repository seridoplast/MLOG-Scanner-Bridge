# MLOG Scanner Bridge v1.3 — Ciclo por bipe (protótipo)

Esta versão atualiza **somente o MLOG Scanner Bridge**. Não modifica o APK do MLOG nem envia configuração ao CMX Scanner.

## Alterações

- **Não abre mais o botão do MLOG ao entrar na tela.** O clique fica associado ao código recebido pelo broadcast do CMX.
- **Separação comum e com quantidade:** prioriza o controle nativo de teclado do MLOG; só aceita editor de código após foco confiável, para não confundir com quantidade/peso.
- **Bipar atividades:** recebe o código, clica uma vez no botão de teclado da tela, espera o editor aparecer e insere o código recebido. Não realiza toques no local anterior quando o Gboard está visível.
- **Confirmação:** deixa o Enter que o próprio CMX envia agir; se o mesmo código permanecer no editor, tenta a ação Enter do campo e, em último caso, a tecla de confirmação **do próprio Gboard**, validada dentro da janela de teclado. Se não puder identificar o botão com segurança, não clica em posições aleatórias.
- **Consulta/conferência:** tenta refocar o campo de leitura após exibição de resultado, em cada novo bipe.
- **Interface:** mantém preto e branco, ícone da marca, teste passivo de leitura e diagnóstico.

## Limitações importantes

Este protótipo foi verificado estaticamente, **não foi compilado ou testado no coletor nesta entrega**. O resultado depende do conteúdo de acessibilidade exposto pelo Flutter e pelo Gboard. `ACTION_IME_ENTER` pode não ser implementada pelo campo do MLOG. Não há garantia de confirmação em todas as telas nem de duração fixa em milissegundos.

Sem um evento pós-processamento do MLOG, desaparecer o código do campo não prova, por si só, que uma operação de estoque foi confirmada. Não utilize esta versão para confirmar pedidos de produção sem validar cada tipo de tela.

## Gerar APK pelo GitHub

Baixe o ZIP e salve em `Downloads`. No CMD:

```bat
cd /d "%USERPROFILE%\Downloads\MLOG-Scanner-Bridge"
powershell -NoProfile -Command "Expand-Archive -LiteralPath (Join-Path $env:USERPROFILE 'Downloads\MLOG_Scanner_Bridge_v13_Ciclo_Por_Bipe.zip') -DestinationPath . -Force"
git add -A
git commit -m "MLOG Bridge v13 ciclo por bipe"
git push origin main
```

Entre em **https://github.com/seridoplast/MLOG-Scanner-Bridge/actions** e aguarde a compilação verde. Baixe o artefato `MLOG-Scanner-Bridge-v13-APK`, extraia `app-debug.apk` e salve em `Downloads`.

## Instalar/atualizar por ADB (com depuração USB autorizada)

```bat
"%USERPROFILE%\Downloads\platform-tools\adb.exe" devices
"%USERPROFILE%\Downloads\platform-tools\adb.exe" install -r "%USERPROFILE%\Downloads\app-debug.apk"
```

Aparecerá `Success` se a atualização for aceita. Se a assinatura da versão instalada for diferente, não desinstale sem antes salvar as configurações.

## Testes controlados

1. Abra Bipar Atividades, espere sem bipar: o Bridge não deve clicar sozinho.
2. Bipe um código de teste: verifique abertura de campo → preenchimento → confirmação.
3. Repita a leitura de teste e observe se a confirmação ocorre de novo sem lançar `0`.
4. Faça o teste de item por quantidade e depois Consulta com dois códigos distintos.
5. Em falha, entre em **Diagnóstico** no Bridge e copie o status; informe em qual tela ocorreu e se o teclado abriu. **Não envie códigos reais de pedidos, dados pessoais ou senhas.**

A acessibilidade deve ser autorizada no Android; mantenha o Play Protect ativo. O Bridge não exige internet nem coleta histórico persistente de códigos.
