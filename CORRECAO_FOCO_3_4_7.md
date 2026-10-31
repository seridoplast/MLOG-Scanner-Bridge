# MLOG Scanner Bridge v3.4.7 — correção experimental de foco após teclado

## Evidências do diagnóstico de 31/10/2026

- O scanner nativo CMX continuou emitindo broadcasts de leitura.
- O Bridge continuou registrando `SHIZUKU_SCAN_INJECTED_UNVERIFIED`. Isso confirma apenas a execução dos comandos `input`, **não** o lançamento no MLOG.
- O teclado selecionado era `br.com.seridoplast.mlogbridge/.BridgeScanInputMethodService`.
- Após ocultar o teclado, o diagnóstico registrou `mInputShown=false` e `mServedInputConnection=null`. Esse estado, isoladamente, não prova defeito, mas é compatível com uma transição de foco do Flutter.
- O painel flutuante expandido usava uma janela focável, capaz de retirar o foco da atividade MLOG.

## Alterações

1. O painel flutuante não solicita foco do Android nos controles comuns. Ele só pode receber foco quando o operador abre o campo numérico de Bipes automáticos.
2. Quando o teclado Bridge abre e fecha no MLOG, a acessibilidade faz uma tentativa conservadora de liberar o foco de um único campo de texto **vazio** na tela de Separação, desde que a tela esteja reconhecida e sem modal.
3. O botão **Resetar Bipe** também solicita a mesma recuperação de foco, depois de fechar o painel.
4. Foram adicionados eventos de diagnóstico `SHIZUKU_IME_*` e `SHIZUKU_FOCUS_*`, sem registrar os códigos de barras.
5. Nenhum código é reenviado automaticamente. Não há `Back`, `Enter`, toque em coordenadas ou navegação automática como parte da recuperação.

## Limitações e homologação

- A recuperação por `AccessibilityNodeInfo.ACTION_CLEAR_FOCUS` depende do suporte da árvore de acessibilidade do Flutter/MLOG. O retorno da ação não prova que o MLOG contabilizou o item.
- O fluxo é específico para o teclado **Bridge IME** e para a tela de **Separação**. Outras telas/teclados não são alterados.
- A compilação completa do APK deve ser validada no GitHub Actions. A checagem sintática de Java e testes unitários de políticas foram executados localmente.
- Antes de implantar em todos os coletores, testar em **um CMX** com produto de teste: ler 3 unidades, abrir/fechar teclado, ler mais 3 e confirmar exatamente 6 lançamentos. Repetir com a janela flutuante.
- Se persistir, testar temporariamente com Gboard como teclado padrão para isolar interferência do IME legado. Não alterar a assinatura do APK nem limpar dados do aplicativo.
