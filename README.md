# MLOG Scanner Bridge v1.1 — Enter por leitura e repetição estável

Projeto Android para o coletor CMX e MLOG. A v1.0 passou a identificar o botão nativo de teclado da separação, mas foram relatadas três falhas: o segundo bipe não confirmava, a tela de atividades abria o campo cedo demais e às vezes tocava `0`, e uma nova consulta exigia voltar a selecionar a pesquisa.

## Mudanças nesta versão

1. **Enter independente por leitura:** remove o bloqueio antigo `lastEnterCode + 1100 ms`. Cada leitura aceita cria uma nova tentativa, identificada por sequência. Se uma leitura nova chegar antes da verificação anterior, a verificação antiga é cancelada. Não há repetição de Enter por tempo sem verificar se o código continua no editor.
2. **Bipar Atividades de Separação:** não executa o clique do ícone quando a tela abre; ele só é tentado ao receber um código pelo broadcast. Se o teclado estiver visível, não usa toque posicional, porque o mesmo ponto pode coincidir com a tecla numérica `0`.
3. **Consulta/Conferência de venda:** na nova leitura, o código da busca anterior pode ser substituído no campo da mesma tela reconhecida. Após um envio, a preparação da busca pode ser refeita para o próximo produto. O reconhecimento continua válido caso o resultado esconda o título, mas preserve a barra de busca explícita.
4. **Enter protegido:** primeiro solicita `ACTION_IME_ENTER` ao editor acessível; se continuar exatamente o mesmo código, tenta clicar **somente uma tecla explicitamente nomeada** Enter/Buscar/Concluído/Ir no Gboard. Não usa coordenadas no teclado, não clica em `0`, Iniciar, Quantidade ou outras ações.
5. **CMX intacto:** somente recebe broadcasts. Não altera a configuração, os formatos nem o Enter do CMX. Usa o Gboard normal e mantém o tema e a interface monocromática da v1.0.

## Limitações e segurança

- Compilação no Android SDK e teste em coletor real **ainda pendentes**. Estrutura, XMLs e fonte foram revisados estaticamente. Não é possível assegurar Enter em uma tela Flutter que não exponha a ação nem uma tecla de confirmação identificável.
- A entrada só é alterada quando o editor pertence à janela do MLOG, não é senha nem quantidade/peso e a tela é reconhecida. Se existir texto manual em campo não identificado, ele não é sobrescrito.
- Como o CMX pode enviar Enter nativamente, **verifique se existe confirmação duplicada**. A configuração `Confirmar leitura com Enter` no Bridge pode ser desligada.
- O serviço requer autorização explícita de Acessibilidade pelo Android e não solicita acesso à internet. Não desative o Play Protect para usar.
- Não tem reconhecimento visual/OCR universal, apenas elementos de acessibilidade e pontos conhecidos para telas verificadas. Em caso de dúvida, não tocar em posição desconhecida.

## Atualização do repositório pelo CMD

Baixe `MLOG_Scanner_Bridge_v11_Enter_Cada_Bipe.zip` e coloque em Downloads. Execute:

```cmd
cd /d "%USERPROFILE%\Downloads\MLOG-Scanner-Bridge"
powershell -NoProfile -Command "Expand-Archive -LiteralPath (Join-Path $env:USERPROFILE 'Downloads\MLOG_Scanner_Bridge_v11_Enter_Cada_Bipe.zip') -DestinationPath . -Force"
git add -A
git commit -m "MLOG Bridge v11 Enter por bipe e foco recorrente"
git push origin main
```

Acesse https://github.com/seridoplast/MLOG-Scanner-Bridge/actions, abra a compilação verde e baixe o artefato `MLOG-Scanner-Bridge-v11-APK`. Extraia `app-debug.apk` para Downloads.

## Instalar pelo ADB (depuração USB)

Conecte o CMX e autorize a depuração USB:

```cmd
"%USERPROFILE%\Downloads\platform-tools\adb.exe" devices
"%USERPROFILE%\Downloads\platform-tools\adb.exe" install -r "%USERPROFILE%\Downloads\app-debug.apk"
```

A lista precisa mostrar `device` e a instalação deve finalizar com `Success`. A atualização `-r` exige a mesma assinatura que a versão instalada. Em caso de incompatibilidade, NÃO desinstale automaticamente: registre o erro antes de decidir, pois a desinstalação pode apagar as preferências.

## Roteiro de teste antes da operação

1. **Atividades:** abra a tela. O botão de teclado não deve ser acionado ao entrar. Bipe uma atividade de teste; o botão deve abrir a entrada, exibir o código e dar **um** Enter. Bipe novamente o mesmo código após a tela voltar ao estado adequado e verifique o Enter de novo. Quando ocorrer aviso de atividade cancelada, feche o aviso antes de uma nova leitura.
2. **Teclado numérico:** confirme que nunca é digitado `0` pelo Bridge. Se o editor não ficar acessível, o Bridge deve informar isso no Diagnóstico em vez de tocar em posição de teclado aberto.
3. **Consulta:** consulte dois códigos diferentes, um depois do outro, e também repita o mesmo. O segundo bipe deve voltar a preencher o campo de busca sem toque manual.
4. **Separação:** valide um item normal e uma bobina com quantidade, usando pedidos de teste; confirme que o Bridge não altera o campo de quantidade.
5. **Enter duplicado:** se o MLOG executar duas confirmações, desligue o Enter em um dos aplicativos e envie o Diagnóstico do Bridge.

Não valide em pedido real enquanto houver possibilidade de duplo Enter. Para problemas: Bridge → Diagnóstico → Copiar diagnóstico, informando qual tela, se abriu o campo e se o código foi inserido/confirmado (sem dados de clientes).
