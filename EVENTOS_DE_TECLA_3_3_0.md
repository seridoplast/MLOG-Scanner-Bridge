# v3.3.0 — Eventos de tecla opcionais

## O que foi adicionado

A aba **Ajustes → Bipe Zebra** agora permite selecionar dois caminhos mutuamente exclusivos:

- **Profile0: teclado nativo CMX**: preserva a v3.2.9 e o CMX envia código + Enter diretamente ao aplicativo que tem foco.
- **Teste: caracteres como eventos de tecla**: solicita ao firmware saída por **broadcast** e envia o código ao MLOG por um IME Android (`InputConnection.sendKeyEvent`), seguido de Enter. Usa o código já existente do teclado Bridge, antes oculto pelo Profile0.

Para o segundo caminho: habilitar e selecionar o **teclado Bridge** no Android. Acessibilidade é **opcional para tentar preparar o foco** do MLOG; o código e o Enter continuam sendo enviados pelo IME. Se for necessário reativar acessibilidade após atualização, somente o usuário pode autorizá-la.

## Limite técnico indispensável

`InputConnection.sendKeyEvent` envia eventos para a **visualização que já tem foco** no aplicativo conectado ao teclado. Ele **não injeta teclas globais em apps de terceiros**. Assim, não é possível prometer que o MLOG receberá bipes sem abrir o editor se não capturar os eventos nativos do próprio scanner.

O serviço integrado ao hardware Zebra (DataWedge) pode ter permissões e integrações que o app comum Bridge não possui. Não é correto equiparar os dois.

## Teste controlado

1. Ative **Bipe Zebra → Eventos de tecla**.
2. Selecione o teclado Bridge no Android.
3. Entre no MLOG em pedido de demonstração; confira se o campo de leitura está aberto.
4. Leia uma unidade e confira no MLOG. Depois tente três leituras; confirme as quantidades.
5. Se não houver entrada, volte ao **Profile0 nativo**.

Nenhuma versão do Bridge comprova a gravação de cada item pelo MLOG sem integração do fabricante.
