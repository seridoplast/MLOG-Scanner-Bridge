# MLOG Scanner Bridge v1.0 — Botão nativo primeiro

Esta revisão corrige especificamente o caso da tela **Separação** em que o Bridge abria o Gboard, mas não acionava o ícone de teclado dentro do MLOG.

## Sequência de leitura

1. Reconhecer a tela **Separação** pelo conjunto de controles “Pular Item” e “Encerrar Item”.
2. Se um campo de leitura **realmente focado** já estiver disponível, usá-lo; nunca considerar um editor apenas existente como pronto.
3. Caso contrário, clicar no **botão nativo de teclado do MLOG** por um toque de acessibilidade (`dispatchGesture`) na região confirmada pelos prints (720×1440): sem botão Quantidade, x=60%, y=60,3%; com Quantidade, x=44%, y=60,3%. A tentativa por posição só ocorre se o layout e a proporção de tela forem reconhecidos e “Toque guiado” estiver ligado.
4. Esperar o editor exposto pelo MLOG, preenchê-lo com `ACTION_SET_TEXT` e **tentar Enter**. Quando `ACTION_IME_ENTER` declara sucesso, verificar 360ms depois se o código continua no campo; se continuar, tentar uma única vez a tecla Enter/Ir do teclado visível.
5. Se o Flutter não expuser campo editável ou tecla Enter à acessibilidade, interromper com diagnóstico; **não lançar leitura em quantidade nem clicar em iniciar**.

A preparação do campo também é tentada na entrada da tela, antes do gatilho, mas não repete indefinidamente toques: um segundo toque pode fechar o modal do MLOG.

## Preservado

- Tema claro/escuro, ícone existente e demais páginas.
- Conferência de venda, consulta e atividades (sem alterações de algoritmo além do reconhecimento de campo focado/Enter).
- Leitura passiva dos broadcasts do CMX; **nenhum comando de configuração do scanner**.
- Gboard padrão e visível; não alterna teclado e não esconde o teclado virtual.
- Serviço de acessibilidade limitado ao pacote MLOG, sem permissão de internet e sem histórico de códigos.

## Limitações importantes

- O APK **não foi compilado nem testado em hardware neste ambiente**. Esta é uma correção no código-fonte pronta para compilação no GitHub. Ela requer validação em coletor CMX Android 14.
- Toque pela posição conhecida não significa reconhecimento visual universal; se o MLOG mudar o layout ou a resolução, envie o diagnóstico e uma captura. O código não faz OCR de tela.
- MLOG em Flutter pode não expor o input ou a ação Enter; não há garantia de conclusão de bipagem até teste real.
- O Enter do Bridge é ativado uma vez na migração desta versão. Como o CMX também pode transmitir Enter, **teste com pedido não operacional** e desative um dos dois em caso de duplicidade.

## Atualizar o repositório pelo CMD do Windows

Baixe `MLOG_Scanner_Bridge_v10_Botao_Nativo.zip` para Downloads e execute:

```cmd
cd /d "%USERPROFILE%\Downloads\MLOG-Scanner-Bridge"
powershell -NoProfile -Command "Expand-Archive -LiteralPath (Join-Path $env:USERPROFILE 'Downloads\MLOG_Scanner_Bridge_v10_Botao_Nativo.zip') -DestinationPath . -Force"
git add -A
git commit -m "MLOG Bridge v10 clicar botao nativo de leitura"
git push origin main
```

Abra `https://github.com/seridoplast/MLOG-Scanner-Bridge/actions`. Se a execução terminar com sucesso, baixe o artefato `MLOG-Scanner-Bridge-v10-APK`, extraia `app-debug.apk` e coloque-o em Downloads.

## Instalar no coletor via ADB

Conecte USB, ative depuração, autorize o PC no coletor e rode:

```cmd
"%USERPROFILE%\Downloads\platform-tools\adb.exe" devices
"%USERPROFILE%\Downloads\platform-tools\adb.exe" install -r "%USERPROFILE%\Downloads\app-debug.apk"
```

O dispositivo precisa aparecer como `device` e a instalação mostrar `Success`. O `-r` mantém os dados apenas quando a assinatura é compatível. Não desinstale o antigo sem checar isso.

## Primeiro teste

1. Abra o Bridge: Ajustes → **Leitura assistida ON; Preparar campo ON; Toque guiado ON; Confirmar com Enter ON**.
2. Abra um item em **Separação**, espere a preparação. O objetivo é abrir o input DO MLOG ao acionar o botão circulado, não apenas abrir o Gboard.
3. Com um código inofensivo, aperte o gatilho e observe se o código aparece e é confirmado exatamente uma vez.
4. Se falhar: Bridge → Diagnóstico → Copiar diagnóstico. Informe se o botão do MLOG abriu a entrada e se o Gboard apareceu.
