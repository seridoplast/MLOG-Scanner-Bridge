# MLOG Scanner Bridge v2.9.9 — Ferramentas flutuantes compactas

Versão baseada na **v2.9.8**. A leitura normal, o clique pelo gatilho e o **bloqueio de 150 ms** permanecem. O Enter da leitura física continua com o CMX.

## 1. Bolha e painel

- A bolha flutuante usa uma **engrenagem vetorial** (`ic_floating_gear.xml` nativo Android, com fonte `assets/engrenagem_bolha.svg`).
- Diâmetro da bolha: **40 dp**; opacidade visual **50%**. Arrastável; ao tocar, mostra o painel.
- Painel expandido com **16 dp de margens internas em todas as direções**, espaçamento entre opções e largura de 314 dp (ajustada aos limites da tela).
- Permissão do Android **Exibir sobre outros apps** continua necessária; nada inicia sem ação do operador.

## 2. Bipes automáticos — N−1

No painel, o antigo nome **Contador automático** foi substituído por **Bipes automáticos (N−1)**.

**Exemplo:** ao informar 100 e tocar em **Armar 1 bipe físico + N−1**, uma única leitura física do CMX corresponde ao primeiro registro; **as outras 99 tentativas de inserção + Enter** são feitas pelo Bridge somente após a confirmação anterior. A primeira leitura NÃO é repetida pelo contador.

O intervalo mínimo entre repetições continua **155 ms**, mas cada repetição pode levar mais tempo enquanto o Flutter não expuser evidência de conclusão. Se o campo não ficar pronto, o fluxo falha, o Enter não for enviado ou não houver confirmação visual, **o lote para sem emitir os próximos Enters**. Também há **Cancelar bipes automáticos**. O CMX deve estar configurado para o primeiro Enter, e o teclado Bridge deve estar selecionado para o Enter das repetições.

**Atenção:** bipes automáticos são **repetições virtuais da entrada** no MLOG, não disparos sonoros/físicos do leitor. Alteração visual do Flutter não confirma persistência no pedido. Testar primeiro em pedido fictício e conferir contagem efetiva antes de usar em produção. Nunca usar sem validar telas de quantidade/peso.

## 3. Diagnóstico de espaços e caracteres ocultos

A tela inicial **Leitura de códigos** exibe abaixo de **Formato lido** uma observação de caracteres detectados no broadcast original: espaços, Enter/CR, LF, Tab, Unicode invisível e controles. A forma legível do código é mostrada sem esses caracteres.

O `ScannerProtocol.barcode()` **não remove mais espaços/sufixos antes do diagnóstico**. O tratador ativo pode removê-los antes da inserção do Bridge; se estiver desligado, apenas a exibição no teste é normalizada. Um código só de caracteres invisíveis é ignorado para não enviar uma leitura vazia ao MLOG. A identificação da simbologia (Code 39, EAN-13 etc.) segue dependendo dos metadados do CMX.

**Limite importante:** o Bridge só identifica CR/LF/Tab quando fazem parte do dado de código recebido por broadcast. Se o CMX enviar Enter como tecla separada, não há como afirmar que estava embutido no código.

## 4. Atualizar pelo CMD

```bat
cd /d "%USERPROFILE%\Downloads\MLOG-Scanner-Bridge"
powershell -NoProfile -Command "Expand-Archive -LiteralPath (Join-Path $env:USERPROFILE 'Downloads\MLOG_Scanner_Bridge_v299_Engrenagem_Bipes_Inspecao.zip') -DestinationPath . -Force"
git add -A
git commit -m "Bridge v299 engrenagem discreta bipes automaticos e diagnostico"
git push origin main && del /q "%USERPROFILE%\Downloads\MLOG_Scanner_Bridge_v299_Engrenagem_Bipes_Inspecao.zip"
```

O ZIP só será apagado quando `git push` tiver êxito. Se `git commit` não gerar alterações (ex.: projeto já atualizado), `git push` ainda poderá executar.

Baixe o artefato **MLOG-Scanner-Bridge-v299-APK** em https://github.com/seridoplast/MLOG-Scanner-Bridge/actions.

### ADB

```bat
"%USERPROFILE%\Downloads\platform-tools\adb.exe" devices
"%USERPROFILE%\Downloads\platform-tools\adb.exe" install -r "%USERPROFILE%\Downloads\app-debug.apk"
```

## 5. Validação

Foram executados testes locais Java independentes do Android (incluindo o cálculo de N−1 e a inspeção de caracteres ocultos) e validação de integridade de XML/ZIP. A compilação completa em Android e o teste em CMX/MLOG ainda precisam ser executados no GitHub Actions e em um coletor de teste.
