# MLOG Scanner Bridge v2.9.6 — 200 ms + teclado compacto

Base preservada: **v2.9.5**. O Enter automático continua sendo responsabilidade do **CMX**, não do Bridge.

## Mudanças

- `TimedClickGate.COOLDOWN_MS = 200L`: desbloqueio pelo relógio após 200 ms. Nenhum clique recusado deve ser guardado para executar depois.
- Teclado Bridge dentro do MLOG: inicia em **barra compacta**. Se o usuário abrir o QWERTY com `Digitar`, ele volta a ficar compacto ao fechar a entrada, mudar de campo ou utilizar o Enter manual; se o Enter físico chegar à IME, também recolhe. Um `restartInput` do mesmo campo não interrompe a digitação.
- Fora do MLOG: teclado expandido permanece disponível normalmente.
- Barra compacta no MLOG: `Digitar` | `⌫ Apagar` | `⇄`. O botão Apagar remove a seleção ou um caractere à esquerda do cursor, diretamente pela `InputConnection`. É necessário haver um campo editável ativo; não executa cliques na tela do MLOG.
- Todos os seis ajustes exibidos começam ligados: Leitura assistida, Toque guiado, Detectar teclado de produtos, Usar teclado Bridge, Leitura rápida, Pré-foco físico. Migração única na v2.9.6; alterações posteriores do usuário são preservadas. **Selecionar o Bridge como teclado padrão ainda depende do Android.**
- Logos introduzidas anteriormente preservadas.

## Cuidado com o intervalo de 200 ms

A média de processamento observada na v2.6 foi de **576 ms**. O prazo de 200 ms desbloqueia *cliques*, não comprova que o Flutter concluiu a leitura. Cliques duplicados ou leituras perdidas podem reaparecer em rajadas. Teste primeiro com pedido de teste.

## Gboard e primeiro bipe

Não há no código do Bridge uma ordem para abrir Gboard na primeira leitura, e o Bridge não altera o teclado padrão do sistema automaticamente. Se aparecer uma mensagem de erro do Gboard, verifique em **Ajustes > Teclado Bridge > Selecionar teclado** se o Bridge está selecionado ou se o Gboard está ativo. Trocar o teclado padrão durante a bipagem pode provocar uma transição de IME, mas isso **não está comprovado como causa** do erro relatado.

Se o erro persistir com o Bridge selecionado, capture o log próximo ao erro:

```bat
"%USERPROFILE%\Downloads\platform-tools\adb.exe" logcat -c
REM Reproduza o erro do primeiro bipe no MLOG.
"%USERPROFILE%\Downloads\platform-tools\adb.exe" logcat -d -v time > "%USERPROFILE%\Downloads\Bridge_v296_Primeiro_Bipe.txt"
```

Não envie o log completo sem revisar informações sensíveis (o Android pode registrar dados de outros apps).

## Atualizar e gerar o APK

Salve `MLOG_Scanner_Bridge_v296_200ms_Teclado_Oculto_Apagar.zip` em Downloads. No CMD:

```bat
cd /d "%USERPROFILE%\Downloads\MLOG-Scanner-Bridge"
powershell -NoProfile -Command "Expand-Archive -LiteralPath (Join-Path $env:USERPROFILE 'Downloads\MLOG_Scanner_Bridge_v296_200ms_Teclado_Oculto_Apagar.zip') -DestinationPath . -Force"
git add -A
git commit -m "Bridge v296 200ms teclado compacto e apagar"
git push origin main
```

No GitHub Actions, baixe `MLOG-Scanner-Bridge-v296-APK` da execução bem-sucedida e extraia `app-debug.apk` para Downloads:

```bat
"%USERPROFILE%\Downloads\platform-tools\adb.exe" devices
"%USERPROFILE%\Downloads\platform-tools\adb.exe" install -r "%USERPROFILE%\Downloads\app-debug.apk"
```

## Testes de aceitação

1. No MLOG, com Bridge selecionado, tocar em `Digitar` mostra QWERTY; ao fechar e abrir o mesmo campo novamente, a barra deve mostrar somente os botões compactos.
2. Em MLOG, `⌫ Apagar` deve apagar seleção ou um caractere de um campo ativo; fora do MLOG, o QWERTY inicia expandido.
3. Depois do Enter manual do teclado Bridge, o QWERTY deve recolher. O Enter enviado pelo CMX não deve ser duplicado pelo Bridge.
4. Em Ajustes, os seis controles devem iniciar marcados depois da atualização; desmarcar manualmente deve persistir nas próximas aberturas.
5. Ler 10 itens lentamente, depois sequências rápidas em um pedido de teste, conferindo duplicações e leituras perdidas.

**Status de validação:** testes locais de temporização, fila e deduplicação; validação do ZIP. Compilação Android e comportamento físico no coletor precisam ser validados por GitHub Actions e teste no aparelho.
