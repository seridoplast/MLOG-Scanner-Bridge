# MLOG Scanner Bridge + Central de Operações — v3.6.15

## Windows (portátil)
- Substitui o painel nativo Win32 simples por uma janela independente do Microsoft Edge em modo aplicativo, com interface moderna integrada.
- Executável portátil sem instalador; precisa do Microsoft Edge ou Google Chrome instalado no Windows 10/11.
- Coletores online e offline, nomes editáveis, versão do Bridge, Android, modelo, bateria, Shizuku, acessibilidade, última ordem reconhecida e atividade recente (indicativa).
- Pedidos identificados são apenas códigos distintos recebidos pelo painel enquanto os aparelhos estão conectados; não representam separações confirmadas pelo MLOG.
- Conclusões exibidas são exclusivamente as observações de pedido explicitamente concluídas e associadas ao equipamento.
- Banco de dados preservado em `%APPDATA%\Seridoplast\ObservacoesPedidos\dados.json`.

## Bridge Android
- Novo campo para alterar o nome de operador/coletor em Ajustes → Observações e comunicados.
- Telemetria do coletor enviada junto com a sincronização das mensagens.
- Compatibilidade com versões antigas do painel (repetição do heartbeat no formato legado se retornar HTTP 400).
- Pré-release com `rollout=0`, sem distribuição automática.

## Catálogo e GitHub Actions
- Removida toda publicação via GitHub Pages. Não é mais necessário ativar Settings → Pages.
- `releases.json` publicado na branch `main` com GitHub Actions, sem GitHub Pages.
- A fonte de atualização no APK é `https://raw.githubusercontent.com/seridoplast/MLOG-Scanner-Bridge/main/releases.json`.
- Workflow separado atualiza no horário programado, ao editar Releases, ou manualmente. O workflow do APK também tenta atualizar após publicar.
- Requer a permissão do workflow para escrever na branch `main`; se houver regras de proteção, será necessário permitir esse bot ou configurar o método de publicação.

## Cuidados
- Somente rede corporativa privada confiável. O protocolo legado usa chave compartilhada por descoberta UDP e HTTP local sem TLS; não exponha as portas TCP 8765 e UDP 8766/8767 à internet.
- As medições de atividade recente são indiretas e não devem ser usadas como controle individual de produtividade.
- Esta versão NÃO instala APKs remotamente nos coletores; os dados são base para um futuro atualizador da frota.
- A compilação do APK Android precisa ser confirmada pelo GitHub Actions e as permissões do Windows verificadas no PC real.
