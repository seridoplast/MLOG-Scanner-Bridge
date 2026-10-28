# MLOG Scanner Bridge v3.4.4 — CMX TC60

## Interface
- Aviso de configuração inicial exibe **somente** acessibilidade e botão flutuante pendentes, em vermelho.
- Removidos os pedidos insistentes de teclado, notificação do Shizuku e pedido de permissão POST_NOTIFICATIONS. Notificação legada 34301 da v3.4.3 cancelada.
- Substituição de recursos visuais com LOGO_ICONE.svg (ícone Android e cabeçalho) e LOGO_NOME.svg (nome no cabeçalho). Versões claro/escuro.
- No botão flutuante: **1. Resetar Bipe** (reenvia broadcast exclusivo), **2. Leitura** (tratador), **3. Bipes automáticos**, este último apenas se liberado nos Ajustes.
- No fim de Ajustes, **Bipes automáticos** fica desligado por padrão e só pode ser ativado com senha de operação. Desligar não requer senha. A senha é validada por hash local e **não constitui proteção contra engenharia reversa do APK**.

## Bipes automáticos + Shizuku
- 1 bipe físico e N-1 leituras com o mesmo mecanismo Shizuku de texto + Enter da versão anterior.
- Aplicado **somente na tela Separação** se a acessibilidade expuser o cartão SEPARADO com código e unidade do produto.
- Exige que o contador visual aumente **exatamente 1** para reenviar o próximo bipe, caso contrário cancela; sem reenvio cego após timeout.
- Em caso de mudança de produto, janela, desconexão do Shizuku, contagem inesperada ou nova leitura física, o lote é interrompido.
- A confirmação visual **não comprova sincronização com o servidor**. Testar com 2 unidades e comparar com o pedido antes de aumentar a quantidade.
- Modo normal legado preservado. Sem mudança na política de duplicidade para leituras manuais.

## Validação
- Teste Java unitário de senha incluso e checagens locais de estrutura. **O APK precisa ser compilado no GitHub Actions** com o keystore anterior e validado em um CMX de teste.
