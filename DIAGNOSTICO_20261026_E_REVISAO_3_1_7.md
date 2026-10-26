# Diagnóstico 26/10/2026 e revisão v3.1.7 — apenas teste controlado

## Evidências objetivas do ZIP Diagnostico_MLOG_20261026_081003.zip

- 25 eventos `BROADCAST_ACCEPTED_DIRECT` no intervalo analisado, para 9 `SCAN_START` completos capturados.
- 9 `NATIVE_ENTER_VERIFIED_IME_SENT` não implicam 9 gravações. O método Android `sendKeyEvent` só confirma que a chamada foi enviada à InputConnection, **não** que o Flutter processou o produto.
- 6 finais `campo_mudou`, 1 `campo_saiu` e 3 finais `sem_confirmacao_cmx`. Alguns bipes foram recebidos enquanto a aplicação aguardava o Flutter.
- Três esperas sem confirmação consumiram 1557, 1573 e 1713 ms; em uma rajada a fila chegou a 8 pendências e foi apagada na versão 3.1.6.
- Há 5 broadcasts ignorados por resfriamento/eco e um por deduplicação. Sem identificador único da leitura no driver não é possível provar quais eram ecos versus bipes físicos distintos.
- No vídeo, a tela **Separação** mostra mudanças na contagem de produtos durante a execução. O XML de acessibilidade contém contadores como `0 CX SEPARADO` e `0 PC FRACIONADO`.

## Modificações conservadoras nesta revisão

- Introduz `SeparationVisualReceipt`: extrai produto, unidade e quantidade visual de SEPARADO ou FRACIONADO; admite que o cartão possa permanecer no mesmo produto com o editor inalterado após o Enter.
- O evento `SEPARATION_VISUAL_QUANTITY_INCREMENT` permite liberar a fila quando a contagem exibida sobe no mesmo produto, sem esperar o editor sumir. Isso é **evidência visual**, não confirmação de persistência no banco do MLOG.
- Quando não houver evidência suficiente, a fila não é mais apagada automaticamente. Fica suspensa, preservando os códigos recebidos apenas na memória. O operador recebe um aviso e deve comparar a quantidade no MLOG; depois, na página inicial do Bridge, há `Descartar pendentes APÓS conferir`.
- Enquanto suspensa, novos bipes são explicitamente recusados (em vez de executar algo arriscado). O operador precisa parar e verificar o MLOG.
- Mantém o controle nativo do leitor, o modo de teclado e a assinatura corporativa do projeto. Nenhum Enter extra é enviado nesta atualização.

## Limitações que não podem ser corrigidas apenas por ajustar milissegundos

Sem acesso ao código-fonte, a uma API de conferência, ou a um retorno de confirmação do MLOG, o Bridge **não consegue garantir exatamente uma gravação por bipe**. A mudança visual pode ocorrer atrasada, e o processamento assíncrono pode usar outra tela/campo. O método comprovadamente robusto exige o MLOG aceitar eventos com ID único e confirmar o registro após persistência.

Esta versão é experimental; não utilizar em pedidos reais até validar por produto e por unidade. Não compilar/instalar em todos os coletores sem teste.

## Recomendação de teste

1. Mantenha a versão anterior para recuperação e faça o ensaio somente em pedido de demonstração.
2. Um bipe e conferir quantidade; cinco bipes com espaçamento; depois rajada de 5.
3. Se a fila suspender, **pare de bipar**, confira o MLOG e somente então abra o Bridge > Leitura de códigos > `Descartar pendentes APÓS conferir`.
4. Capture novo diagnóstico. Compare bipes recebidos, incrementos de quantidade no MLOG e itens persistidos.

## Compilação

O ZIP contém código-fonte e teste puro Java. A compilação do APK Android precisa ocorrer no GitHub Actions com as credenciais de assinatura já configuradas. Sem aparelho físico e SDK Android local, o comportamento do Flutter/IME não foi validado aqui.
