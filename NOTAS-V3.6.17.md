# MLOG Scanner Bridge / Central de Operações — 3.6.17

## Alterações
- Aviso geral direcionado: selecionar um ou mais coletores online ou enviar a todos.
- Painel registra destinatários e mantém o acompanhamento individual de disponibilidade, recebimento e leitura.
- Avisos e observações novas acionam um toque curto no canal de notificação, respeitando modo silencioso e volume do Android; não toca a cada atualização do painel.
- Cartão flutuante de mensagens mais compacto (306dp de largura, altura limitada a 390dp), texto e espaçamento reduzidos, animação breve.
- Renomeação feita no PC permanece pendente até o Bridge informar no heartbeat seguinte o nome salvo, evitando perder a alteração antes da confirmação.
- Executável único de Windows com ícone do APK embutido como recurso de ícone PE/Windows, e favicon correspondente na interface.
- Informações menos importantes (IP, versão Android, acessibilidade e contador redundante) removidas dos cartões principais; restante disponível na telemetria armazenada.
- GitHub Actions: versão 3.6.17 aprovada para Release estável/Latest e rollout=100.

## Uso
1. Feche a Central antiga antes de abrir o novo EXE. O banco permanece em `%APPDATA%\Seridoplast\ObservacoesPedidos\dados.json`; mantenha backup.
2. Na aba Avisos gerais, selecione 'Todos os coletores online' ou 'Selecionar coletores'. Apenas os online são elegíveis.
3. Substitua os arquivos da raiz do repositório pelo ZIP Android e faça `git add .`, `git commit -m "v3.6.17"`, `git pull --rebase origin main`, `git push origin main`.
4. Após o build assinado, confirme no GitHub a Release estável v3.6.17 e teste no TC60.

## Limites e segurança
- `rollout=100`: após a publicação todos os coletores elegíveis poderão atualizar.
- Não força instalação se Shizuku, integridade do APK ou condições de ociosidade impedirem.
- As notificações dependem de áudio do Android e das permissões já autorizadas; uma entrega só é 'vista' ao tocar em 'Entendido'.
- O painel local usa HTTP na rede privada; não exponha TCP 8765 nem UDP 8766 à internet.
- A compilação Android completa é executada pelo GitHub Actions; os testes do servidor e sintaxe do frontend foram executados localmente.
