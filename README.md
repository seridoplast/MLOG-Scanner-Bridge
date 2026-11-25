# Atualização 3.6.21

**ADB integrado exclusivo; avisos e janelas flutuantes suspensos.** Para configuração, migração e limitações, veja [ATUALIZACAO-v3.6.21.md](ATUALIZACAO-v3.6.21.md). As notas abaixo documentam versões anteriores e não representam as funções ativas desta versão.

# MLOG Scanner Bridge — Seridóplast

**Versão:** 3.6.16 | Pacote Android: `br.com.seridoplast.mlogbridge`

## Novidades da v3.6.16 — Central de Frota

- **EXE único:** use `Seridoplast-Central-Operacoes-v3.6.16-UNICO.exe`. Não precisa instalar ou extrair arquivos auxiliares. É necessário Microsoft Edge ou Chrome instalado no Windows para desenhar a janela.
- **Histórico offline:** nome, última versão, bateria, Shizuku, último pedido, atividade e até 12 últimas observações associadas ficam salvos no banco, mesmo quando o coletor está offline. A exclusão de uma observação também a remove dos históricos.
- **Dashboard de inatividade:** "Sem atividade há 20 min" indica coletor **online** sem evento recente da acessibilidade do MLOG por pelo menos 20 minutos; não mede produtividade nem garante que esteja sem uso.
- **Atualização da frota:** no painel, solicitar consulta à **Release estável oficial** de todos os coletores online ou de um coletor específico. O Bridge continua obedecendo a versão, o rollout, a assinatura, a inatividade e as condições de instalação. O painel não força instalação nem envia APKs arbitrários.
- **Shizuku:** o painel pode solicitar uma **notificação de ajuda no coletor** quando ele estiver desligado. A ativação privilegiada ainda exige intervenção do operador, depuração sem fio autorizada, ADB ou root; não ocorre automaticamente no Windows.
- **Acompanhamento:** a Central registra a solicitação por dispositivo, a entrega e a confirmação de agendamento/aviso, sem confundir essas etapas com instalação concluída.
- **Compatibilidade:** atualizar o programa Windows primeiro e depois instalar a v3.6.16 do Bridge em coletor piloto. O Bridge antigo não entende os novos comandos de gestão.

## O que mudou

- **Painel Windows portátil** com interface moderna em janela de aplicativo independente, sem abas de navegador. O executável contém o servidor Go e todos os recursos visuais.
- **Gestão de coletores:** cada separador pode mudar o nome do seu coletor em **Bridge → Ajustes → Observações e comunicados → Nome**, ou o operador pode renomear pelo PC. A mudança feita no PC chega ao coletor quando ele voltar a se conectar.
- **Telemetria:** status online/offline, versão do Bridge, modelo, Android, Shizuku, acessibilidade, bateria, pedido observado e sinais de atividade recente no MLOG. O painel lembra coletores offline após reiniciar.
- **Pedidos identificados:** número de identificadores distintos reportados por equipamento ao painel, enquanto há conexão. **Não** é uma contagem oficial de separações concluídas.
- **Conclusões confirmadas:** pedidos com observações explicitamente marcadas como concluídas, vinculados ao identificador do coletor. Pedidos sem observações não geram essa contagem.
- **Catálogo de Releases:** atualização estática via `releases.json` na branch `main`; **não usa mais GitHub Pages** nem a ação `configure-pages`. A consulta de fallback à API permanece limitada.

## Executar o painel no Windows

Baixe o arquivo `Seridoplast-Central-Operacoes-v3.6.16-UNICO.exe` e dê dois cliques. Não há instalador nem pasta de dependências.

- Requer Windows 10/11 64 bits com **Microsoft Edge** ou **Google Chrome** instalado: usa o motor existente para abrir uma janela sem barra de URL nem abas comuns, com a aparência do painel web.
- O executável tem o servidor HTTP e todos os arquivos embutidos. Não precisa instalar Python, Go, serviços externos ou instalador.
- O banco antigo é mantido em `%APPDATA%\Seridoplast\ObservacoesPedidos\dados.json`. Faça backup antes de trocar o programa.
- **Feche completamente versões anteriores** antes de abrir. Apenas uma instância pode usar TCP 8765.
- Para comunicação na LAN, execute `CONFIGURAR-FIREWALL-PRIVADO.cmd` como administrador uma única vez. TCP 8765, UDP 8766 e anúncios multicast UDP 8767 devem ser permitidos na rede privada.
- Mantenha o aplicativo aberto para receber os coletores. Não há instalação de serviço que abra automaticamente com o Windows nesta versão portátil.

## Publicar a nova versão do Bridge

Substitua os arquivos na raiz do repositório (não coloque uma pasta `MLOG-Scanner-Bridge` dentro de outra) e execute:

```shell
git add .
git commit -m "v3.6.16 - Dashboard de frota e acoes remotas seguras"
git push origin main
```

O workflow **Gerar APK do MLOG Scanner Bridge** compila e assina. A `v3.6.16` será **pré-release com `rollout=0`**, para teste em coletor piloto, não instalada automaticamente na frota. O APK só é confiável se passar na verificação da assinatura corporativa do Actions.

## Solução para os erros repetidos do GitHub Actions

- O workflow `Atualizar catalogo publico de Releases` não utiliza `actions/configure-pages` nem precisa que o Pages esteja habilitado.
- Ele gera `releases.json` e faz commit do arquivo na branch `main` com a credencial de Actions de escopo `contents: write`.
- É acionado quando uma Release é editada/publicada, manualmente e a cada hora. A compilação do APK tenta também gerar o catálogo depois de publicar a versão.
- Confira `https://raw.githubusercontent.com/seridoplast/MLOG-Scanner-Bridge/main/releases.json`. O JSON inclui `latest` (última Release estável) e `releases` (até quatro publicações recentes).
- **Se houver proteção de branch ou permissão bloqueada**, o push do bot poderá falhar. Autorize o GitHub Actions a gravar `releases.json` na `main` ou use outra estratégia de publicação; não coloque um token secreto no APK.
- Execuções antigas com X vermelho continuam no histórico, mas novas execuções deixam de tentar usar Pages.

## Restrições de operação e segurança

- **Online:** o PC recebeu heartbeat do coletor nos últimos 75 segundos, aproximadamente. **Offline:** não recebeu; não significa obrigatoriamente que o equipamento esteja desligado.
- **Atividade recente:** o serviço de acessibilidade detectou eventos de clique, edição de texto ou rolagem no MLOG nos últimos 120 segundos. Não é prova de que a pessoa está separando um pedido.
- **MLOG em primeiro plano:** indicador baseado em eventos de janela acessíveis no Android; pode não ser conclusivo em todas as versões do Flutter.
- O Bridge não recebe um evento oficial de pedido finalizado no ERP, portanto não estima pedidos efetivamente separados. A reconciliação exata depende de integração do Target MLOG/ERP.
- O protocolo de observações continua usando HTTP local e chave compartilhada via descoberta de rede. Utilize **apenas em LAN corporativa isolada**. Não exponha TCP 8765 e UDP 8766/8767 à internet.
- O painel agora solicita **buscas de Releases estáveis**; a instalação, quando elegível, é realizada pelo atualizador existente do Bridge e não é garantida no instante da solicitação.
- A compilação Android e o teste de reconhecimento de pedido no TC60 devem ser feitos no GitHub Actions e no coletor real.

## Compilar o painel Windows manualmente

Com Go 1.23+ instalado, na pasta `operador-windows`:

```bat
set GOOS=windows
set GOARCH=amd64
set CGO_ENABLED=0
go build -trimpath -ldflags="-H windowsgui -s -w" -o Seridoplast-Central-Operacoes.exe .
```

O aplicativo não leva credenciais dentro do executável; elas são geradas pelo servidor local ao criar o banco de dados.
