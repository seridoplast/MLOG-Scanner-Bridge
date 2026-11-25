# MLOG Scanner Bridge 3.6.19 — teste de ADB local

## Instalação e teste
- Esta versão é publicada inicialmente como pré-release. Instale em um coletor piloto pelo cabo, mantendo o Shizuku instalado e autorizado.
- Em Ajustes > ADB integrado (teste), toque em Parear ADB. Abra as configurações de desenvolvedor > Depuração sem fio > Parear dispositivo com código. Com o diálogo aberto, responda à notificação do Bridge com a porta de pareamento e o código de seis dígitos, separados por espaço. Autorize notificações se solicitado. O código não é armazenado.
- O Bridge procura a porta de conexão via mDNS; se necessário, informe a porta da tela principal de depuração sem fio (diferente da porta de pareamento). A conexão é somente com 127.0.0.1. O pareamento depende da autorização Android. Não desinstale o Shizuku até testar a leitura, recuperação por Volume, reinício e instalação automática.
- O cliente tenta reconectar enquanto o serviço estiver ativo. Não consegue habilitar silenciosamente a depuração se o Android desativar o adbd ou deixar de disponibilizar sua porta. Não há bypass de autorização nem shell remoto arbitrário pela Central.
- A atualização do próprio Shizuku ainda utiliza o caminho Shizuku original.

## Atualizações
- Busca normal: aproximadamente 10 minutos. Watchdog periódico: 15 minutos. APK pendente: tentativas em cerca de 2 minutos, quando inativo e autorizado. Android/Doze pode atrasar todos os intervalos.
- O canal automático continua exclusivo de Releases estáveis. As próximas pré-releases incluem rollout=100, que só vale depois da promoção a Release. Para pré-releases antigas, editar a descrição para rollout=100 ao desmarcar Pre-release e marcar Latest. rollout=0 em uma Release estável continua sendo uma pausa deliberada. Não troque o APK assinado após aprovar.
- O workflow de catálogo publica em release-catalog/releases.json, sem GitHub Pages, e mantém o espelho na main para versões antigas. A publicação reage a edições de Releases e tem verificação agendada. Se necessário execute Atualizar catalogo publico de Releases manualmente no Actions. A branch será criada na primeira execução bem-sucedida.
- Preserve .git e releases.json existentes ao copiar este projeto. Não envie uma exclusão de releases.json: antes de git add, execute git restore -- releases.json caso ele esteja rastreado e tenha sido alterado/excluído. O espelho legado ainda pode criar commits na main quando o catálogo mudar.

## Validação e limites
Testes Java/Python de política e catálogo e testes Go da Central executados. O cliente ADB integrado depende de libadb-android e seu build Android completo, pareamento, leitura, instalação e comportamento no TC60 ainda precisam ser validados. O executável Windows foi compilado para amd64, sem teste gráfico em Windows neste ambiente. A Central mostra ADB local, Shizuku e estado da atualização reportados pelo coletor. Use rede corporativa privada.
