# MLOG Scanner Bridge v0.8 — Foco estável

**Aplicativo experimental** para coletor CMX e MLOG 9.3.5. Projeto Android Java, sem conta de usuário, rede ou envio de dados.

## O que mudou nesta versão

- **Não configura mais o scanner CMX.** Removidos comandos para formato de código, modo broadcast, modo teclado, Enter, parar/iniciar scanner e botões que disparavam esses comandos. O Bridge apenas escuta broadcasts de leitura; o CMX Scanner oficial continua configurando o hardware.
- **Pré-foco ao entrar na tela.** Em telas identificadas com segurança, tenta focar o editor de código antes do próximo disparo do gatilho. Caso o editor não exista, a tela *Bipar Atividades de Separação* tenta acionar o botão do teclado, pelo nó de acessibilidade ou, como último recurso, por toque proporcional da captura (720×1440). O toque acontece no máximo uma vez por visita, com uma segunda tentativa limitada quando um clique semântico não abre o campo.
- **Consulta adicionada.** Reconhece `Consulta`, `Local de estoque`, `Bipe ou digite endereços, produtos ou atividades` e o campo superior de leitura.
- **Preservadas:** Busca da conferência de venda, separação comum e tentativa experimental de foco em produtos (desativada por padrão).
- **Tema:** o botão de lua só reage a toque real na tela. Enter/Tab do scanner não pode alternar tema; ao atualizar, a preferência de tema é restaurada uma vez para `Sistema`.
- **Ícone:** marca vetorial baseada no SVG fornecido, tonalizada de acordo com a cor do texto da tela; ícone de instalação com preenchimento até as bordas. O formato final arredondado/circular é imposto pelo lançador Android.
- **Enter opcional:** desativado por padrão. Evite ligar simultaneamente o Enter no CMX e no Bridge, pois pode duplicar confirmações.

## Limitações (importante)

1. **Sem garantia de funcionamento em todas as telas Flutter.** A acessibilidade do Android pode não revelar o campo, embora ele apareça na tela. Nesse caso o Bridge não deve digitar em campos desconhecidos.
2. **Leitura via teclado/wedge:** quando o CMX fornece só teclas (sem broadcast), a página de teste do Bridge não recebe o valor; o pré-foco ainda poderá deixar o MLOG pronto para a digitação nativa, se a tela permitir.
3. **Gestos relativos:** são uma medida auxiliar de teste nas telas capturadas. Se o MLOG mudar de layout/resolução, desative `Toque guiado` em Ajustes. Não usar com pedidos reais até conferir campo e dados.
4. **Serviços CMX x Bridge:** mantenha o motor de leitura nativo. Se dois serviços de acessibilidade disputarem toques na tela, teste a automação separadamente, sem alterar as configurações de leitura do CMX.
5. **Play Protect / Acessibilidade:** a permissão deve ser aprovada pelo Android; este projeto não tenta contornar proteções nem desativá-las.

## Instalação / atualização pelo CMD (Windows)

### Enviar ao GitHub

Salve `MLOG_Scanner_Bridge_v8_Foco_Estavel.zip` em `Downloads`:

```bat
cd /d "%USERPROFILE%\Downloads\MLOG-Scanner-Bridge"
powershell -NoProfile -Command "Expand-Archive -LiteralPath (Join-Path $env:USERPROFILE 'Downloads\MLOG_Scanner_Bridge_v8_Foco_Estavel.zip') -DestinationPath . -Force"
git add -A
git commit -m "MLOG Bridge v8 foco estavel sem comandos CMX"
git push origin main
```

No [GitHub Actions](https://github.com/seridoplast/MLOG-Scanner-Bridge/actions), abra a execução mais recente que estiver verde. Em **Artifacts**, baixe `MLOG-Scanner-Bridge-v8-APK`, extraia e copie `app-debug.apk` para a pasta Downloads do Windows.

### Instalar com ADB (via USB / depuração autorizada)

```bat
"%USERPROFILE%\Downloads\platform-tools\adb.exe" devices
"%USERPROFILE%\Downloads\platform-tools\adb.exe" install -r "%USERPROFILE%\Downloads\app-debug.apk"
```

`device` indica conexão autorizada; `unauthorized` exige confirmar a janela no coletor. **Não desinstale a versão anterior automaticamente**: a instalação pode falhar se os APKs estiverem com certificados diferentes, caso em que é necessário planejar a migração das configurações.

## Teste em campo

1. No CMX Scanner original, mantenha a configuração atual que funciona (incluindo Enter se já está habilitado).
2. Em Ajustes do Bridge, marque `Leitura assistida`, `Preparar campo ao abrir tela` e `Toque guiado`; deixe `Enviar Enter automático` **desligado** e `Foco experimental em produtos` **desligado**.
3. Abra a tela `Consulta` no MLOG: o campo superior deve ficar selecionado. Faça uma leitura de código de teste.
4. Abra `Bipar Atividades de Separação`: o Bridge tentará abrir o botão de teclado automaticamente antes da leitura. Se abrir o campo, bipar um código de teste; não confirmar movimentações reais nesta etapa.
5. Teste `Busca da conferência de venda`, já funcional na v0.7. Depois, em ambiente de teste, avalie produtos, bobinas e quantidade.
6. Se uma tela falhar, abra **Diagnóstico** no Bridge e copie a mensagem. Não habilite cliques genéricos em quantidade ou outros campos.

**Construção:** Gradle 8.11.1 / Java 17 / Android SDK 35 / `:app:assembleDebug`. Compilação executada pelo GitHub Actions; não há SDK Android neste ambiente para validar APK aqui.
