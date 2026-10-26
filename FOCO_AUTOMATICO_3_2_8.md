# v3.2.8 — Preparação automática de foco (experimental)

- Em **Bipe Zebra**, quando o MLOG abre uma tela de Separação/Conferência conhecida, a acessibilidade tenta abrir o campo de leitura **antes do primeiro bipe**.
- A acessibilidade é usada **somente para abrir/focar o campo**; o código e Enter seguem pelo teclado Android (IME).
- Tentativas limitadas e espaçadas; ignora telas de login, diálogos e sobreposições.
- Mantém os dois modos exclusivos, e a opção de abertura automática pode ser desligada.
- Inclui indicação do modo atualmente ativo na tela inicial do Bridge.

**Condições:** selecione Bipe Zebra, habilite e escolha o teclado Bridge no Android, e mantenha a acessibilidade ativa para abertura automática. Sem permissão de acessibilidade, o Android não permite que o Bridge clique no botão do MLOG.

**Limitação:** InputConnection só existe quando o Flutter cria um campo editável. A preparação automática depende do layout acessível do MLOG e precisa ser testada no coletor. O diagnóstico anterior mostrava o Bipe antigo ativo durante parte relevante da gravação.
