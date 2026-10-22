# Por que a v3.0.2 parava depois de duas leituras?

Na v3.0.2 o Bridge tratava `performEditorAction()` do IME como sucesso de Enter, sem confirmar se Flutter realmente reagiu. Após a primeira repetição virtual, se o contador visual `SEPARADO` não subisse, cancelava o lote em 1.800 ms. O diagnóstico anterior também mostrou Enters enviados que não correspondiam a registros visíveis.

## Experimento v3.0.3

- Usar KEYCODE_ENTER DOWN/UP na conexão IME como nas versões antigas, que demonstraram melhor reação de Enter.
- Não disparar repetições virtualmente a 155 ms; usar no mínimo 900 ms entre Enters, mais 180 ms para texto no campo estabilizar antes da tecla.
- Observar contador visual onde disponível, mas não parar exclusivamente por ausência de incremento; contar separadamente Enters enviados e incrementos observados. Mudanças inesperadas de produto, salto do contador e falha de foco continuam abortando.
- Confirmar resultado **no pedido**, não apenas pela contagem de Enters.

Sem teste real no CMX não há garantia de que esta abordagem elimine leituras perdidas.
