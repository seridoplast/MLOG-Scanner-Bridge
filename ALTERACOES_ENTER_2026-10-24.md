# Ajuste experimental — Conferência de Venda / Enter

- O código anterior aguardava o Enter do CMX passivamente e desistia após 1,1 s.
- Agora, somente nas telas de conferência homologadas, após 650 ms com o mesmo código ainda no campo e **sem observar Enter físico depois do início da espera**, tenta um único Enter via teclado Bridge, serviço de acessibilidade (Android compatível) ou ação IME.
- A tentativa única evita loops de Enter. O retorno das APIs **não comprova** que o MLOG gravou a conferência: validar visualmente no coletor.
- Bipes durante uma leitura pendente continuam descartados por segurança, mas agora há aviso explícito. Não foi ativada fila automática porque poderia contabilizar produtos incorretamente se a tela Flutter estiver atrasada.
- Requer serviço de acessibilidade ativo; a opção do teclado Bridge melhora a disponibilidade da conexão IME. Se o CMX envia Enter sem expor evento de tecla ao serviço, ainda existe risco de confirmação duplicada: testar com pedidos de teste antes de usar em produção.
- Não foi possível fazer análise comportamental completa do `cmxscanner.apk` apenas pelo binário, nem testar em coletor físico neste ambiente.
- Assinatura corporativa e workflow GitHub Actions foram preservados. O APK precisa ser compilado no GitHub Actions.
