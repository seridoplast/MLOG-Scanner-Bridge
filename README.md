# MLOG Scanner Bridge — Seridóplast

**Versão:** 3.6.8 · Pacote: `br.com.seridoplast.mlogbridge`

## Novidades da v3.6.6
- Bolha flutuante de 40 dp com **15% de opacidade em todo o conjunto** (fundo preto e ícone). A área de toque invisível continua com 48 dp, e o painel expandido conserva a legibilidade.
- Busca manual em **Sobre → Buscar versões** limitada às **4 Releases mais recentes** do Bridge; a API do GitHub solicita apenas quatro registros e o aplicativo também aplica limite defensivo. Inclui estáveis e pré-releases (testes protegidos por senha).
- GitHub Actions publica **v3.6.6 como Release estável** no primeiro push bem-sucedido, com `rollout=100`. Isso habilita atualização automática da frota elegível; teste/controle de distribuição deve ocorrer antes da publicação.
- Mantidos a busca automática aproximada de 10 minutos, tentativas de instalação a cada ~2 minutos, o Shizuku, painel responsivo ao tema e recuperação por Volume.

## Publicação da v3.6.6

1. Extraia o ZIP e substitua o código do clone local.
2. Execute:
   ```bash
   git add .
   git commit -m "v3.6.6 - Bolha 15 por cento e quatro releases"
   git push origin main
   ```
3. O GitHub Actions compila e assina o APK. Se a tag `v3.6.6` ainda não existir, a publica diretamente como **Release estável** com `rollout=100`.
4. **Aviso de distribuição:** `rollout=100` disponibiliza a atualização automática para todos os coletores elegíveis. Para um teste controlado, não publique essa configuração sem antes reduzir o rollout no workflow.
5. O ZIP contém o projeto; o APK de instalação é gerado no Actions com a chave de assinatura corporativa.

## Funcionalidades anteriores (v3.6.4)

- **Painel flutuante atualizado:** ferramentas em cartões e ações redesenhadas, com estado Shizuku mais legível. O painel continua sem roubar foco do MLOG, exceto quando o campo numérico autorizado exige teclado.
- **Janela de atualizações do Bridge:** seleção em cartões arredondados com versão, estado ESTÁVEL ou TESTE e aviso de versão já instalada. Mantém bloqueio com senha para pré-releases e botão de instalação por Shizuku ou Android após a validação.
- **Atualizações oficiais do Shizuku:** seção independente em **Sobre**, com consulta manual, verificação diária opcional, download do APK e instalação manual. Fonte: `https://github.com/RikkaApps/Shizuku/releases`. Somente Release estável; valida pacote `moe.shizuku.privileged.api`, versão mais recente e certificado igual ao do Shizuku já instalado. Se o digest SHA-256 da Release estiver disponível, ele também é validado.
- **Automatizar o Shizuku:** verificação + download diários e instalação enquanto o coletor está inativo podem ser habilitados separadamente. **Ambos os controles começam desligados.** A instalação automática exige confirmação expressa e Shizuku autorizado, e pode ser atrasada pelo WorkManager (tentativas aproximadamente a cada 15 minutos). A atualização do próprio aplicativo Shizuku pode encerrar o serviço privilegiado e exigir novo início via ADB.
- **O Bridge segue com busca de atualizações a cada ~10 min**, e tentativas de instalar APK corporativo pendente a cada ~2 min. Sem alteração da leitura/Volume, senha de pré-release ou assinaturas do Bridge.

## Cuidados específicos do Shizuku

- Atualizar o **aplicativo** Shizuku não garante que o **serviço** privilegiado continue ativo após a instalação. Se parar, pode ser preciso reativá-lo no Shizuku ou via ADB. Nunca atualizar durante a separação/conferência.
- Em coletor onde Shizuku não esteja instalado, o Bridge **não instala automaticamente** uma primeira cópia. Instale o app oficial primeiro.
- O processo verifica a identidade da assinatura do APK contra o Shizuku existente; se o app instalado tiver outra assinatura, a atualização é **bloqueada**.
- O botão **Instalador Android** solicita confirmação padrão do sistema. A opção **Via Shizuku (teste)** usa o instalador privilegiado e pode ter retorno de sessão interrompido ao atualizar o próprio Shizuku.
- O WorkManager não garante os prazos configurados quando o Android limita tarefas em segundo plano.

## Observações operacionais

- A recuperação rápida por **Volume** foi preservada (pausa de 10 ms entre os toques na micro-navegação em telas compatíveis).
- O último código e o histórico de 4 leituras aparecem somente enquanto a Activity estiver ativa; não são salvos em preferências.
- O código do Bipe Normal permanece por compatibilidade, mas a interface oferece apenas Bipe Shizuku.
- O teste real em coletor é indispensável para confirmar a aparência e a instalação automática.
- A assinatura release depende dos GitHub Actions Secrets já configurados; não inclua certificados ou senhas no repositório.


### v3.6.7 — Compatibilidade de leituras (piloto)

- Remove somente caracteres de enquadramento do broadcast CMX (CR/LF/TAB/NUL/STX/ETX e BOM) antes de enviar pelo Shizuku. Não altera zeros à esquerda nem dígitos.
- Rejeita caracteres de controle **internos** em vez de apagar separadores GS1 silenciosamente.
- Aceita códigos simples com hífen, ponto, barra, sublinhado e mais, além dos alfanuméricos.
- Adiciona uma pausa de 110 ms entre `input text` e `Enter` para permitir ao Flutter processar o texto.
- O resultado do comando ADB **não confirma** que o MLOG aceitou o código ou encontrou um produto. Não há retentativa automática do mesmo bipe para evitar duplicação.
- Observação: o exemplo `7890000000105` **não passa na validação GTIN-13** (dígito correto para os 12 primeiros é 9). O Bridge entrega o valor original e não corrige etiquetas; o MLOG pode rejeitar essa referência por regra própria.
- Primeira publicação prevista como pré-release (`rollout=0`) para testar no coletor piloto.

## v3.6.8 — Correção do limite GitHub API

- O Bridge consulta primeiramente um catálogo estático e público em
  `https://seridoplast.github.io/MLOG-Scanner-Bridge/releases.json`.
- O catálogo inclui as últimas quatro Releases para seleção manual (estáveis e testes)
  e, independentemente delas, a última Release estável para a busca automática.
- O workflow compila e assina APKs como antes; só o workflow de GitHub Actions
  gera o JSON com metadados oficiais, SEM tokens nos coletores.
- Continua verificando o SHA-256 e o certificado corporativo antes de instalar.
- Em caso de indisponibilidade do catálogo, usa API de forma controlada:
  consultas automáticas de contingência no máximo uma a cada 4 horas por coletor;
  consultas manuais de contingência no máximo a cada 15 minutos.
  Em `403/429` por limite, respeita `X-RateLimit-Reset`/`Retry-After` e aguarda.
- **Configuração única obrigatória para remover a dependência da API:** no GitHub,
  acesse `seridoplast/MLOG-Scanner-Bridge` → **Settings → Pages → Build and deployment →
  Source: GitHub Actions**. Depois acesse **Actions → Atualizar catalogo publico de
  Releases → Run workflow**, e confirme o JSON no endereço acima.
- Publicar novas Releases dispara a atualização do catálogo. Aprovações manuais
  (`Edit release`) também disparam. Um agendamento de hora em hora é redundância.
- Se o Pages não estiver habilitado, o Bridge continua funcionando com a consulta
  reserva mais espaçada, mas a frequência automática de 10 minutos dependerá da
  disponibilidade do catálogo para não esgotar a cota REST.
- O APK da 3.6.8 ainda precisa ser compilado pelo GitHub Actions e testado no coletor.
- **A v3.6.8 agora é publicada diretamente como Release estável** com `rollout=100` e marcada como `Latest`, após um `git push` e build bem-sucedido. Não será criada como pré-release.
- Se `v3.6.8` já existir como pré-release, o mesmo workflow a promove automaticamente para **Release estável + Latest** e ajusta `rollout=100`, sem sobrescrever um APK já publicado. Se o APK estiver ausente, adiciona o binário assinado.
- O job de Pages publica em seguida o catálogo contendo a nova versão estável. Se o Pages estiver desativado, o app dependerá da consulta de contingência à API, sujeita à limitação temporária.
- **Cuidado:** `rollout=100` permite distribuição a toda a frota elegível. Se um coletor já estiver executando a própria 3.6.8, ele não reinstalará a mesma versão automaticamente.

### Publicar a v3.6.8 como Release estável

Após substituir os arquivos do repositório local:

```bash
git add .
git commit -m "v3.6.8 - Publicacao direta como Release estavel"
git push origin main
```

Acompanhe a compilação e a publicação em **Actions**. Ao final, confirme na página de Releases que `v3.6.8` está marcada como **Latest**, **não** como *Pre-release*, com `rollout=100` no corpo e com o arquivo `MLOG-Scanner-Bridge-v3.6.8.apk` anexado. Em seguida, confirme que `https://seridoplast.github.io/MLOG-Scanner-Bridge/releases.json` contém `latest.tag_name` igual a `v3.6.8`. Se necessário, execute o workflow **Atualizar catalogo publico de Releases**.

**Nota:** alterar o estado de pré-release para estável e marcar como Latest são ações diferentes; o texto `rollout=100` também é necessário para os coletores do Bridge elegíveis à instalação automática.

## v3.6.9 (pré-release) — Observações de pedidos

Inclui painel Windows em `operador-windows` e suporte ao sino no botão
flutuante. Os coletores acessam o painel via LAN `http://IP:8765` com chave
aleatória de 64 caracteres. O servidor só aceita a administração pelo próprio PC;
a rede local recebe apenas pedidos autenticados para consulta e conclusão.

**Como funciona:** cadastre uma ou mais observações usando o número do pedido
completo, incluindo zeros à esquerda. Quando o Bipe Shizuku ler o número na
tela de **Bipar pedidos** reconhecida pela acessibilidade, o Bridge consulta
as observações. Se existirem, abre o painel do sino e mostra o contador. Enquanto
o pedido estiver em foco, consulta mudanças a cada aproximadamente 20 segundos.
Leituras de produtos em **Separação** não acionam essa busca.

Para concluir, é obrigatório tocar **Marcar pedido como feito** e confirmar no
coletor após finalizar a separação no MLOG, pois ainda não há um sinal seguro
de conclusão do próprio MLOG. A conclusão só é removida do coletor quando o
servidor confirma o recebimento. No painel, a observação aparece como **Feito**
com horário e identificação do aparelho.

**Segurança:** o canal HTTP local usa chave de acesso, porém não criptografa o
tráfego. Configure somente em Wi-Fi corporativo isolado; não exponha a porta
8765 na Internet. O Bridge bloqueia servidores com IP fora das faixas privadas.
O usuário precisa conceder as permissões normais de sobreposição e acessibilidade.

### Compilar o painel Windows

O painel foi escrito em Go 1.23+ (biblioteca padrão, sem serviços externos).
Na pasta `operador-windows`, execute:

```bat
set GOOS=windows
set GOARCH=amd64
go build -o Seridoplast-Observacoes-Pedidos.exe .
```

Para testes do servidor: `go test ./...`. Para executar o painel, basta abrir o
`.exe` em Windows 10/11 x64; não requer Go instalado. O banco fica em
`%APPDATA%\\Seridoplast\\ObservacoesPedidos\\dados.json`. Consulte
`operador-windows/LEIA-ME.txt`.

## v3.6.10 — Observações sem cadastro individual e avisos gerais

- O Windows envia descoberta em **UDP 8766** (responde apenas para origem na mesma sub-rede privada) e serve a API em **TCP 8765**.
- A API entrega uma chave da instalação do painel no anúncio de descoberta LAN; isso oferece configuração sem digitação, mas pressupõe **rede corporativa confiável e isolada**. Não exponha as portas à internet nem use redes públicas.
- A bolha busca o painel automaticamente e envia batimentos a cada aproximadamente 15 segundos. O painel exibe o estado dos coletores online e permite transmitir avisos gerais somente aos conectados quando o operador confirma o envio.
- A confirmação **Entendi** marca o comunicado como visto para o coletor. Observações vinculadas ao pedido continuam separadas dos comunicados.
- O antigo controle **Leitura** foi removido e o tratamento de leitura fica ativado. **Resetar Bipe** também saiu da bolha. Bipes automáticos com senha continuam opcionais.
- A atualização mantém permissões existentes, mas Android não permite conceder sobreposição, acessibilidade ou Shizuku silenciosamente. PC e coletor precisam estar na mesma rede, com TCP 8765 e UDP 8766 permitidos no firewall de rede privada.
- Versão experimental **v3.6.10**, publicada como **pré-release** (`rollout=0`) após compilação no Actions.


## v3.6.11 — Observações pela tela e confirmação por coletor (pré-release)

- O número do pedido é lido **do cabeçalho visível do MLOG** via árvore de acessibilidade, sem bipar o pedido e sem OCR. A foto de referência mostra `136122` ao lado de `Conf. de Entrada`. O mesmo critério cobre cabeçalhos compatíveis de separação/conferência. **Somente números expostos pela acessibilidade podem ser reconhecidos.**
- Consulta o painel pelo número exato, preservando zeros à esquerda. Nunca usa códigos de produto como número de pedido.
- O sino abre automaticamente quando há observações. Janela simplificada: pedido, observações e avisos gerais.
- Ao aparecer um **novo pedido**, o anterior fica em *Aguardando conclusão*. Só depois é permitido tocar em **Concluir pedido** e confirmar. A simples mudança de tela **não marca pedidos como feitos** automaticamente, pois pode ocorrer cancelamento ou troca de operador.
- Várias conclusões pendentes podem ficar na fila do coletor até a confirmação. O painel do PC sincroniza após a confirmação.
- Ícones de ações do Bridge padronizados em vetores monocromáticos; os ícones do MLOG original não são modificados.
- Os comunicados gerais agora distinguem **destinado**, **disponibilizado na resposta HTTP**, **confirmado pelo aplicativo** e **visto (Entendi)**. O painel mostra por equipamento. Coletores antigos não confirmam recebimento por protocolo, embora ainda possam registrar *Visto*.
- Mantém descoberta automática por rede privada e recursos de leitura Shizuku existentes.
- **Pré-release / rollout=0**: teste primeiro no coletor piloto. A compilação Android depende do GitHub Actions.

### Teste do pedido na tela

1. Abra uma tela do MLOG com o número do pedido no cabeçalho.
2. Execute `TESTAR-PEDIDO-TELA-ADB.cmd` no Windows com `adb` no PATH.
3. Se o número não aparecer no XML da acessibilidade, o Bridge não terá como lê-lo com confiabilidade por esse método. Nesse caso envie o XML e registro da tela do MLOG, sem usar OCR silencioso.
4. Cadastre a observação no painel, abra o pedido e verifique o sino; mude para outro pedido e então confirme a conclusão do pedido anterior no sino.

### Atualização do painel Windows

O programa foi gerado em `Seridoplast-Observacoes-Pedidos-v3.6.11.exe` (arquivo de entrega separado). Feche a versão anterior e execute a nova, mantendo a mesma pasta/dados. Um aviso destinado a 10 coletores **não implica confirmação de 10**: acompanhe a lista individual no painel.
