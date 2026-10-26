# MLOG Scanner Bridge 3.2.0 — experimental

**Diagnóstico 26/10/2026 (09:24):** 40 broadcasts do leitor para o mesmo código numérico de 13 dígitos com dígito verificador válido, durante a separação de dois produtos diferentes. Isto sugere que o MLOG avançou para o próximo item enquanto a etiqueta anterior ainda era lida. Não é possível inferir se um código válido pertence ao pedido sem integração com o MLOG.

## Proteções

- Bloqueia leitura GTIN brasileira 789/790 de 13 dígitos quando checksum inválido; se firmware declara EAN/UPC, exige checksum. Preserva códigos internos de outros comprimentos e tipos.
- Quando a tela Separação muda de produto, bloqueia **o mesmo código** usado no produto anterior. Nenhum Enter será enviado; o operador deve ler a etiqueta do novo produto. Exige que o cartão do MLOG esteja legível pela acessibilidade.
- Preserva a fila/Enter temporizados da v3.1.9. Não consulta contagens de produtos após Enter nem fecha mensagens do MLOG automaticamente.

## Gatilho longo (desligado por padrão)

- Ajustes > Leitor do coletor > Segurar gatilho 2 s = leitura continua (teste).
- Precisa de controle nativo ativo e serviço de acessibilidade com evento do gatilho. Após 2 segundos pressionado, tenta `com.service.scanner.scanmode=continuous` e `com.service.scanner.start.scanning`; ao soltar, envia `com.service.scanner.stop.scanning` e restaura modo `trigger`.
- Intervalo de novas tentativas do firmware nunca menor que 250 ms. O intervalo configurado para o Enter continua sendo respeitado pelo Bridge.
- Failsafe de 30 s se o evento ACTION_UP não chegar. Alguns firmwares podem não permitir iniciar/parar por broadcast durante o botão pressionado. Validar em um único coletor.
- Desligar o checkbox ou interromper a acessibilidade para a repetição; essas ações também enviam STOP.

## Limitações

A validação identifica GTIN com checksum inválido e a etiqueta antiga após mudança visível de produto, mas não consegue provar que um código válido faz parte do pedido. Broadcast enviado não confirma que o firmware iniciou leitura contínua. Sem Android SDK, esta versão foi apenas submetida aos testes puros Java; compilar pelo GitHub Actions, instalar em um único coletor e homologar com pedido de treinamento.
