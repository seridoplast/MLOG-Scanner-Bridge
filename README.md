# MLOG Scanner Bridge v0.9 — Teclado visível e Enter assistido

Projeto de testes para coletores CMX/Hikrobotics com Android 14 e MLOG (pacote `com.targetsistemas.target_mlog2`). Compilação no GitHub Actions, sem Android Studio local.

## Mudanças em relação à v0.8

- **Gboard permanece visível**: removida a supressão automática do teclado virtual. Não precisa alternar o teclado padrão.
- **Enter assistido**: após preencher um campo com `ACTION_SET_TEXT`, aguarda cerca de 260 ms e só tenta Enter se o **mesmo código ainda estiver no campo**. Primeiro usa `ACTION_IME_ENTER`, depois, se houver uma tecla Enter/Ir/Buscar **identificada pela acessibilidade** na janela do teclado, tenta acioná-la. Nunca clica em Iniciar/Quantidade/Cancelar por conta própria.
- **Código já inserido pelo CMX**: caso a leitura nativa tenha preenchido o campo, espera ~300 ms e tenta confirmar se a entrada não mudou. Evita sobrescrever a leitura.
- **Telas de pedido/separação**: reforçadas as tentativas de localizar o botão de teclado pela acessibilidade e pelo pareamento com o botão de câmera. Para os **dois layouts de separação das capturas** (com e sem Quantidade), existe uma posição relativa opcional, condicionada ao reconhecimento de “Pular Item” e “Encerrar Item” e à proporção de tela compatível.
- **Atividades**: mantém o clique no ícone inferior e aguarda o campo antes do preenchimento. Não oculta o teclado e não repete toques automaticamente apenas porque passou tempo.
- **Consulta e conferência**: mantém o reconhecimento do campo superior; a confirmação é posterior ao preenchimento.
- **CMX Scanner**: somente escuta as leituras por broadcast; **não modifica** formatos, modos de leitura, sufixos ou configurações do scanner original.
- **Interface**: preserva o tema claro/escuro e remove a opção de ocultar o teclado. O Enter do Bridge fica **ligado após a migração** (pode desligar em Ajustes).

## Limitações e segurança

- Ainda é **protótipo não testado num coletor físico pelo autor do código**. Compilar no GitHub não comprova o funcionamento dos fluxos de Flutter.
- O Flutter pode não expor o editor ou o comando Enter à acessibilidade. Nesse caso o Bridge deve registrar uma mensagem no **Diagnóstico**, e não deve clicar aleatoriamente.
- Se o CMX também estiver enviando Enter via modo teclado, pode haver **confirmações duplicadas** dependendo do MLOG. Comece os testes fora da operação real. Se houver duas confirmações, **desative o Enter do Bridge** e use apenas o CMX, ou vice-versa (conforme o seu modo de leitura).
- **Não armazena histórico de códigos nem pede acesso à internet.** Precisa da acessibilidade autorizada legitimamente pelo Android; não tenta contornar o Play Protect.
- Fallback por posição é limitado ao aspecto próximo de 720 × 1440 e às telas reconhecidas. Outras resoluções, alterações de interface, zoom e teclados podem exigir ajustes.

## Subir para o GitHub via CMD

1. Extraia o conteúdo deste ZIP diretamente na pasta de trabalho do repositório (não dentro de subpasta adicional).
2. Execute:

```cmd
cd /d "%USERPROFILE%\Downloads\MLOG-Scanner-Bridge"
git add -A
git commit -m "MLOG Bridge v9 teclado visivel e enter"
git push origin main
```

3. Em GitHub **Actions** baixe o artefato `MLOG-Scanner-Bridge-v9-APK` (caso a compilação termine verde) e extraia `app-debug.apk` para Downloads.

## Instalação por ADB

Conecte o coletor à USB e autorize a depuração. Em CMD:

```cmd
"%USERPROFILE%\Downloads\platform-tools\adb.exe" devices
"%USERPROFILE%\Downloads\platform-tools\adb.exe" install -r "%USERPROFILE%\Downloads\app-debug.apk"
```

A instalação com `-r` só atualiza sem remover dados se a assinatura digital for compatível. Se aparecer `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, confira a assinatura; **não desinstale sem antes avaliar a perda de configurações**.

## Primeiros testes

1. Em **Ajustes**: Leitura assistida ON; Preparar campo ON; Toque guiado ON; Detectar teclado de produtos ON; Confirmar leitura com Enter ON; Gboard como teclado padrão.
2. Teste “Bipar Atividades de Separação” com código de teste que não afete pedidos reais. Verifique se o MLOG abre o campo e confirma.
3. Teste a separação de um produto comum e, depois, de um produto com Quantidade, conferindo **qual campo recebeu a leitura**.
4. Teste “Consulta” e “Busca da conferência de venda”.
5. Se falhar, abra **Diagnóstico → Copiar diagnóstico** e envie a mensagem com captura da tela específica. Se mostrar “MLOG/Gboard não disponibilizaram Enter”, a tela Flutter não está oferecendo uma ação de confirmação segura; será necessário ajustar ao elemento real do MLOG.

## Observação sobre a fonte do Enter

O evento Enter configurado no CMX e o Enter acionado via acessibilidade **não são o mesmo mecanismo**. Um Enter físico enviado antes da abertura do campo pode se perder; por isso o Bridge tenta confirmar depois de inserir o código. Não há garantia de que o MLOG trate `ACTION_IME_ENTER` como a tecla física Enter.
