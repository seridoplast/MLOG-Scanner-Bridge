# MLOG Scanner Bridge v3.1.3 — Enter próprio e Conferência de Entrada

- Novo checkbox em **Ajustes > Enter da conferência > Enviar Enter pelo Bridge (teste)**. Desligado por padrão, para comparar.
- Neste modo, primeiro executa `AccessibilityNodeInfo.performAction(ACTION_IME_ENTER)` no editor focado (Android 11+), que é o mesmo mecanismo observado na rotina `CmxAccessibility.dispararEnter` do APK enviado. Caso o Android recuse, tenta o par DOWN/UP `KEYCODE_ENTER` via teclado Bridge ou conexão da acessibilidade (Android 13+).
- Aguarda 180 ms, valida o código ainda no editor do MLOG, foco, tela homologada e ausência de Enter físico registrado. Um evento de Enter capturado pelo serviço durante a leitura é filtrado no modo próprio, porém **não há garantia de interceptação de todos os Enters do CMX**.
- **Obrigatório desativar o Enter original do CMXScanner**: procurar o **Modo B (broadcast puro)** encontrado no APK do CMX, e desligar o sufixo Enter, conforme as opções disponíveis no aparelho. antes de testar o modo próprio. O Bridge não muda preferências internas do app CMXScanner e não consegue bloquear entradas de todos os métodos OEM. Não desligar o leitor/broadcast (fonte do código de barras).
- Mapeamento adicional para **Conf. de Entrada** no layout enviado (720×1440): título `Conf. de Entrada`, botão `BIPAR PRODUTOS`, teclado à direita da câmera, posição aproximada (412, 999) na captura. Prioriza nó de acessibilidade verificável e só usa toque guiado com tela reconhecida. O layout inicial da Conferência de Entrada **não foi fornecido** e não recebeu coordenadas.
- Modo desligado mantém a recuperação tardia de Enter nas telas mapeadas já existente na v3.1.2.
- Leituras rápidas ainda podem ser descartadas quando o MLOG está ocupado; não há confirmação de estoque sem validação visual nem fila automática segura.
- Testar em pedido de treinamento, com exatamente 1, 2 e 5 leituras, sem CMX enviar Enter, e conferir quantidade efetivamente registrada.
- Preservadas chave de assinatura do GitHub Actions e preferências existentes. **ZIP com fontes; não é APK compilado**.
