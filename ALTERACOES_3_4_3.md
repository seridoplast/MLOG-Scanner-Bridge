# MLOG Scanner Bridge v3.4.3 — Monitor do Shizuku

Base: v3.4.2 confirmada funcionando no TC60 (Android 14).

- Mantém **integralmente** o mecanismo de leitura Shizuku da 3.4.2 (`Shizuku.newProcess`, input text e Enter), as configurações de broadcast CMX e proteção de fila.
- Página inicial e Ajustes: mostra estado atualizado do Shizuku e botão **Abrir Shizuku / Iniciar serviço**. O painel da engrenagem flutuante também oferece acesso rápido.
- Ao abrir o Bridge com Shizuku selecionado e serviço desligado: abre o Shizuku **uma única vez por abertura do Bridge**. O operador toca manualmente em **Iniciar**. Não tenta pressionar botões de outro aplicativo.
- Serviço de acessibilidade monitora Shizuku em segundo plano a cada 5 segundos. Notificação acionável se parar (requer permissão de notificações no Android 13+); a notificação desaparece quando voltar.
- Ao reiniciar: receiver de boot tenta publicar lembrete (se o Android permitir). A depuração sem fio e a inicialização do Shizuku continuam manuais.
- Interface com apenas **Bipe Shizuku** e **Bipe Normal**. O antigo Bipe Zebra foi removido da seleção; antigas preferências Zebra migram uma vez para Bipe Normal se Shizuku não estava selecionado.
- Sem root, sem cliques de acessibilidade em Shizuku, sem repetição automática de leituras e sem tentativas de reenviar códigos durante desconexão.

## Validação no CMX
1. Instalar a APK v3.4.3 assinada com a **mesma chave** da 3.4.2.
2. Abrir o Bridge, confirmar **Bipe Shizuku** e Shizuku **ativo**.
3. Ler códigos diferentes e o mesmo código intencionalmente em duas leituras.
4. Encerrar o Shizuku com o MLOG aberto: verificar aviso e que o Bridge não injeta códigos.
5. Abrir Shizuku pela notificação, tocar em **Iniciar**, voltar ao MLOG e testar.
6. Reiniciar coletor: verificar aviso e recuperar via Shizuku sem computador.
7. Se notificação não aparecer, autorizar notificações do Bridge em Ajustes do Android.

**Atenção**: Shizuku sem root não é auto-iniciável após reboot no Android 14. Este pacote facilita a recuperação, mas não substitui uma solução privilegiada de inicialização no firmware.
