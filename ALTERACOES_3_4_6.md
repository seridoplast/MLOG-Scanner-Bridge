# v3.4.6 — aviso de Shizuku restaurado

- Restaura a notificação de Shizuku indisponível **somente quando Bipe Shizuku estiver selecionado**.
- Notificação persistente com atalho para abrir o aplicativo Shizuku (sem roubar o foco do MLOG).
- Some automaticamente quando a conexão privilegiada Shizuku é restabelecida ou ao selecionar Bipe Normal.
- Verificação a cada 5 segundos enquanto a acessibilidade estiver ativa, e nos eventos de ligação/desligamento do Shizuku.
- Android 13+: solicita uma única vez a permissão de notificações, apenas no modo Shizuku. Se negada, o usuário deve habilitar manualmente nas configurações de notificações do Bridge.
- Mantém os ajustes visuais da v3.4.5 e todo o fluxo de leitura da v3.4.4.
- O Bridge **não inicia o daemon do Shizuku automaticamente**: o toque na notificação abre o aplicativo Shizuku para iniciar manualmente caso a automação externa falhe.
