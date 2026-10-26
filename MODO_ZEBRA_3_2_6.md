# MLOG Scanner Bridge v3.2.6 — teclado inspirado no DataWedge Zebra

## Origem e limites da comparacao

O diagnostico do coletor Zebra mostrou leituras rapidas com DataWedge, mas nao provou se o perfil do MLOG usava Keystroke Output ou Intent Output. A documentacao Zebra confirma que a opcao Keystroke Output usa um InputMethodService para entregar texto e teclas. Esta versao e uma **hipotese experimental baseada nesse mecanismo**, nao uma copia confirmada do perfil DataWedge.

## Novo modo: Scanner nativo -> broadcast -> teclado Bridge (IME) -> MLOG

- Opt-in (vem DESLIGADO), em Ajustes / Modo Zebra — teclado direto.
- Usa o servico de hardware `com.hikrobotics.pdaservice` e o broadcast `br.com.seridoplast.mlogbridge.SCAN` com o codigo em `ScanCode`.
- O teclado Bridge recebe o codigo e usa `InputConnection.commitText()` de uma vez; depois tenta Enter por `InputConnection.sendKeyEvent(DOWN/UP)` e, se ambos forem recusados, tenta `performEditorAction`.
- **Nao consulta a arvore da acessibilidade a cada bipe**. Ao ativar este modo, o receptor e os eventos de leitura da AccessibilityService antiga param de processar bipes.
- Exige que o teclado Bridge seja habilitado e selecionado pelo usuario nas configuracoes do Android. Isso e obrigatorio: um app comum nao pode escolher o teclado padrao silenciosamente.
- O campo de leitura do MLOG precisa estar focado. Sem InputConnection viva para o pacote `com.targetsistemas.target_mlog2`, nao envia codigo. Nao tenta tocar no botao do teclado por coordenada.
- Evita confirmar um campo cujo texto nao corresponde ao codigo e nao concatena em campo preenchido. Espera ate 750 ms por campo disponivel; se nao ficar disponivel, descarta a leitura COM AVISO, nao confirma nenhum produto automaticamente.
- Aceita bipes iguais consecutivos; elimina somente o eco exato com menos de 30 ms. Fila de no maximo 48 bipes enquanto ha processamento, sem guardar codigos no disco.
- Respeita o tempo definido em Ajustes entre a insercao e o Enter, com piso de 20 ms para o Flutter absorver `commitText`.
- O MLOG pode recusar a tecla ou demorar a reagir. O retorno `true` de `sendKeyEvent` NAO significa que o produto foi gravado.
- O recurso de gatilho longo via eventos de acessibilidade fica desativado quando este modo esta ativo. O disparo normal continua controlado pelo servico nativo.

## Procedimento de teste sem alterar os demais coletores

1. Gere/instale o APK v3.2.6 com a **mesma assinatura corporativa**.
2. No Android, habilite e selecione `MLOG Bridge - teclado experimental` em Configuracoes > Teclado / Idiomas e entrada.
3. No Bridge > Ajustes, ative `Modo Zebra — teclado direto (teste)`. Aplique a configuracao nativa e confirme a selecao do teclado.
4. Abra uma conferencia de TESTE no MLOG e toque UMA vez no botao de teclado do MLOG para abrir o campo de leitura (nao use campo de senha/busca).
5. Faça 1, depois 3 e depois 10 bipes e confira as quantidades realmente registradas.
6. Para diagnosticar por USB: `adb logcat -s MLOGBridge:I` e procure `ZEBRA_IME_SCAN_QUEUED`, `ZEBRA_IME_CODE_COMMITTED_MS_`, `ZEBRA_IME_ENTER_FROM_COMMIT_MS_` e erros `ZEBRA_IME_*`.
7. Para reverter: desative `Modo Zebra` e selecione novamente o teclado anterior do Android. O modo de acessibilidade antigo continua no projeto, mas pode precisar ser reabilitado no Android.

## Riscos conhecidos e limites

- Este modo nao consegue saber por si so se o codigo existe no pedido, nem se o servidor do MLOG registrou a conferencia.
- A `InputConnection` pode ser recriada pelo Flutter, ou nao expor o texto atual. Nesse caso, a operacao e conservadora: bloqueia se o campo comprovadamente mudou, mas nao pode garantir todos os resultados.
- Se a acessibilidade for desativada, este modo pode funcionar normalmente desde que o teclado Bridge esteja selecionado e o campo do MLOG esteja com foco. Nao e uma garantia em todo firmware.
- Os comandos de configurar scanner alteram preferencias globais do leitor, que podem precisar ser restauradas manualmente.
- Nada deve ser homologado para producao antes de um teste fisico no CMX Supply.
