# MLOG Scanner Bridge v3.2.9 — Profile0 nativo CMX

## Base observada no Zebra
- Perfil `Profile0 (default)` com **Barcode Input** e **Keystroke Output** ativos no vídeo fornecido.
- `configzebra.xml` fornecido contém somente opções de log, não contém o perfil de leitura completo.
- Não foi demonstrado se o Zebra utiliza Basic Data Formatting para acrescentar Enter, nem suas opções de Key Events. A configuração proposta de terminador `newline` é uma hipótese de compatibilidade a validar, não uma clonagem exata do DataWedge.

## Implementação
- Em Ajustes, selecionar **Bipe Zebra · Profile0** solicita ao serviço `com.hikrobotics.pdaservice` saída `keyboard`, terminador `newline`, desligamento de broadcasts e clipboard e scanMode `trigger`.
- O Bridge **não** recebe e redigita códigos, **não** cria Enter, **não** verifica campo e **não** exige seu IME nem acessibilidade para o modo Profile0. O serviço do hardware passa a ser o responsável pelo envio de teclas.
- O modo **Bipe antigo** continua e solicita restauração da saída `broadcast` ao ser escolhido.
- Não habilita gatilho longo no modo Profile0; mantém uma leitura por acionamento físico.
- As configurações do leitor são **globais** e os comandos podem ser recusados silenciosamente por firmwares diferentes. `configureProfile0Keyboard()` retornar true significa somente que os Intents foram enviados.

## Teste físico obrigatório
1. **Um coletor de teste**. Anote versão e configurações anteriores. Atualize mantendo assinatura/dados.
2. Abra **Bridge → Ajustes → Bipe Zebra · Profile0 → Ativar**, toque **Abrir MLOG**.
3. Teste uma leitura sem abrir o botão de teclado do MLOG. Se o MLOG não receber, teste uma vez com o campo aberto para separar *foco* de *saída do scanner*.
4. Teste três leituras do mesmo produto com espaçamento normal e depois rápidas, conferindo quantidades.
5. Se não funcionar, selecione **Bipe antigo** para reconfigurar `broadcast`, sem desinstalar o aplicativo.

## Limites
- Android não permite a um aplicativo comum gerar KeyEvents em qualquer janela arbitrária. Este novo modo delega o teclado ao serviço nativo do fornecedor; a compatibilidade do firmware é condição necessária.
- A detecção de produto/quantidade no MLOG não é confirmada pelo Bridge em modo Profile0: todo bip de teste deve ser conferido pela quantidade registrada.
- O modo Profile0 não usa o botão nem a configuração dos milissegundos do Bridge: ritmo de teclas é gerido pelo firmware. Uma eventual latência do MLOG continua fora de controle.
- Se o firmware exigir foco de editor para a saída `keyboard`, a leitura sem abrir teclado pode continuar não funcionando. Nesse caso, só uma integração específica do MLOG ou firmware resolverá essa diferença.
