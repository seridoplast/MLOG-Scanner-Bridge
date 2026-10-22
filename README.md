# MLOG Scanner Bridge v3.0.2 — Diagnóstico e sincronização

Base: v3.0.1. Preservados: 150 ms de bloqueio do gatilho físico, leitura normal com Enter do CMX, tratador de códigos, formato do CMX, painel com interruptores e gaveta N−1.

## Evidência do diagnóstico 2026-10-22 20:06:27

O log do Bridge registrou `AUTO_COUNT_ENTER_DISPATCHED_2` até `_10` e `AUTO_COUNT_FINISHED`; isso comprova chamadas de envio, mas não a execução no MLOG. O vídeo mostra atualização de unidades `SEPARADO` em alguns momentos, evidenciando processamento parcial/assíncrono. O histórico não comprova que o pedido gravou as dez unidades. O teclado aparecia/ocultava durante o lote.

## Correções

- Repetições: passa a preferir `InputConnection.performEditorAction(imeAction)` quando o editor do MLOG oferecer uma ação válida; fallback para par KEYCODE_ENTER. Mantém 1 tentativa por repetição; não reenvia Enter às cegas.
- Na tela `Separação`, o Bridge lê o total `PC/UN SEPARADO` exibido no cartão do mesmo produto e só passa à próxima repetição quando a contagem subir exatamente uma unidade. Limiar de observação: 1.800 ms. Caso a contagem não mude, interrompe e informa quantos Enters foram enviados e quantos incrementos foram observados. Troca antecipada de produto ou salto maior que 1 interrompe para conferir o pedido.
- Nas outras telas, onde não existe o contador visível `SEPARADO`, as tentativas usam cadência mínima de 650 ms e permanecem explicitamente marcadas como **sem confirmação**. O Bridge não tem acesso ao backend do MLOG e não consegue comprovar gravação.
- Engrenagem: memoriza coordenadas da bolha em preferências ao arrastar e restaura ao recolher o painel, reabri-lo e reiniciar o serviço. A posição do painel expandido nunca substitui a posição armazenada da bolha.
- Primeira execução: apresenta diálogo guiado com acessibilidade, teclado Bridge habilitado/selecionado e permissão de sobreposição. Mostra um aviso vermelho na tela inicial até os três requisitos estarem autorizados. O Android precisa receber a autorização do usuário; nenhum serviço é ativado silenciosamente.

## Teste recomendado

1. Fazer backup/conferir pedido de teste. Usar 3 no contador (1 físico + 2 virtuais).
2. Verificar se o número `SEPARADO` aumenta três vezes no produto correto.
3. Se o lote parar, copiar o diagnóstico; eventos `AUTO_COUNT_SEPARADO_CONFIRMED_N`, `AUTO_COUNT_ENTER_UNCONFIRMED_N`, `AUTO_COUNT_ENTER_BRIDGE_IME` e `AUTO_ENTER_METHOD_EDITOR_ACTION_*` ajudarão a identificar a falha.
4. Validar novamente o total gravado no MLOG. Atualização visual não prova persistência no servidor.

## Atualização

Veja `ATUALIZAR_PELO_CMD.txt` (inclui remoção automática do ZIP após push confirmado).

## Compilação

O Android SDK/Gradle não está disponível neste ambiente. Foram executados testes Java locais das políticas, identificação de incremento e controle de fila; a compilação final e a integração com Flutter exigem GitHub Actions e o coletor.
