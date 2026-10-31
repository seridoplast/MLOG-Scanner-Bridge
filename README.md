# MLOG Scanner Bridge — Seridóplast

Aplicativo Android para os coletores CMX TC60 (Android 14), integrando o scanner físico ao **MLOG** por broadcast e, no modo escolhido pelo operador, comandos pelo **Shizuku**.

**Versão do projeto:** `3.5.6` · **Repositório:** `seridoplast/MLOG-Scanner-Bridge` · **Pacote:** `br.com.seridoplast.mlogbridge`.

## Funcionalidades

- Leitura do scanner nativo CMX por **broadcast exclusivo**; modos **Bipe Shizuku** e **Bipe Normal**.
- Botão flutuante **Resetar Bipe**, recuperação experimental pela tela **Volume** da Conferência de Venda e controle protegido de bipes automáticos.
- Alerta quando o Shizuku não estiver disponível.
- **Atualizador corporativo:** verifica o GitHub Releases, baixa APK de produção por HTTPS, exige SHA-256 idêntico ao digest informado pelo GitHub, confirma pacote, versão superior e certificado da chave corporativa.
- **Instalação pelo Shizuku:** usa sessão do Package Manager (`pm install-create` → `install-write` via `stdin` → `install-commit`). Obtém retorno de cada fase; se o Android interromper o canal com EPIPE, apresenta o erro e oferece instalação pelo Android, com confirmação do operador. O shell não acessa o diretório privado do Bridge.
- **Distribuição gradual:** uma Release publicada com `rollout=0` não instala nada automaticamente. É possível ampliar a liberação para `rollout=10`, `rollout=25`, `rollout=50` e `rollout=100` editando a descrição da mesma Release.

## Pré-requisitos

1. Coletores na internet (GitHub API e download de Releases precisam estar acessíveis).
2. APKs assinados **sempre com a mesma chave corporativa**. Um APK com outra assinatura será rejeitado.
3. Shizuku **iniciado e autorizado** para instalação pelo shell. Se estiver desligado, o APK pode ser baixado, mas a instalação automática fica pendente.
4. O aplicativo MLOG deve ficar fora da tela de operação durante a instalação. O Bridge só tenta atualizar quando a tela está apagada ou quando uma tela de outro aplicativo é identificada de modo conservador, após pelo menos cinco minutos sem leituras Shizuku. Se não conseguir verificar que está livre, adia.

O Shizuku precisa de inicialização adequada após reinicializações. O atualizador **não consegue iniciar o serviço Shizuku sozinho**. A instalação silenciosa depende também do comportamento do firmware CMX, que deve ser validado no aparelho piloto.

## Teste piloto — v3.5.6 (recuperacao rapida por Volume)

**Base:** v3.5.4 corrigida derivada do projeto enviado; preserva o instalador Shizuku que ja foi validado no coletor.

- `versionName`: `3.5.6`; `versionCode`: `1030505` (anterior: `1030504`).
- **Mudanca:** trocar o retorno por KEYEVENT BACK pelo toque na seta interna do MLOG, mantendo o gatilho apos fechar o teclado: um toque no icone **Volume** seguido de toque na **seta de voltar do MLOG**.
- **Tela homologada por print:** **Conferencia de Venda**, com titulo e botao "BIPAR PRODUTOS", em **720x1440**, alvo de abertura **(648,175)** e toque de retorno **(55,103)**. Em outras telas ou resolucoes, a acao e ignorada por seguranca.
- **Rapidez:** pausa experimental de **10 ms entre os comandos**. Sao executados em **uma sessao shell pelo Shizuku**, em vez de dois processos separados com a antiga pausa de 950 ms. **Nao ha garantia de tempo total abaixo de 100 ms**: o Android, o shell e o Flutter adicionam latencia e a navegacao pode levar mais tempo. Se a seta for tocada cedo demais, pode ser necessario aumentar a pausa numa proxima versao.
- **Gatilho:** fechamento do teclado real (observacao de janelas de acessibilidade); debounce reduzido para 60 ms e polling de reserva a cada 120 ms. Ainda existem salvaguardas de leitura pendente, texto nao enviado, overlay, Shizuku indisponivel e intervalo de recuperacao.
- A recuperacao **nao injeta nem repete codigos ou Enter**, e nao promete confirmar o retorno da rota Flutter: o teste em hardware deve validar se a conferencia continua intacta e as leituras voltam.
- **Configuracao:** Ajustes > Bipe Shizuku > "Recuperar leitura ao fechar teclado (Volume)". O botao flutuante "Resetar Bipe" tambem tenta a recuperacao em tela compativel. **O fechamento do teclado serve como gatilho heuristico; o Bridge nao tem confirmacao geral do MLOG de leitura ignorada.**
- O pipeline do GitHub faz **Release `v3.5.6` com `rollout=0`** no push para `main`. Instale manualmente no coletor piloto e **nao libere o rollout** ate confirmar a navegacao e a ausencia de efeitos colaterais.

### Passo de verificacao de uma versao piloto

1. Com Shizuku ativo, abra Conferencia de Venda na tela conforme o print do teste.
2. Abra e feche o teclado; observe a ida e volta ao Volume. Verifique se a tela principal permanece intacta e as leituras voltam a ser reconhecidas.
3. Se o Volume nao abrir, se o Android voltar para outra pagina ou se a operacao for interrompida, **desative** "Recuperar leitura ao fechar teclado (Volume)" e mantenha o `rollout=0`.
4. Registre logs com `adb logcat -d -s MLOGBridge:I` e confira `VOLUME_SHIZUKU_SEQUENCE_OK` e o campo `elapsed_ms`. O status confirma execucao de comandos, **nao** processamento da interface pelo Flutter.

## Teste da correção de instalação — v3.5.2

**Mudanças frente à v3.5.1:** correção e diagnóstico do instalador Shizuku, que anteriormente tentava enviar todo o APK a `pm install` em uma única etapa e podia receber `write failed: EPIPE (broken pipe)`. A nova implementação cria uma sessão, transmite o APK e só depois solicita o commit; coleta resposta/erro de cada etapa e abandona sessões que falham. Se a instalação Shizuku falhar ou estiver indisponível, o operador pode escolher **Instalar pelo Android**, sem desinstalar o Bridge e sem ignorar a verificação do APK.

O `versionName` passou a `3.5.2` e o `versionCode` a `1030502`. **Não alteramos** `applicationId`, certificado fixado, modo de scanner nem política de liberação gradual. A chave da assinatura continua nos Secrets do GitHub.

Teste somente em **um coletor piloto**:

1. Mantenha instalada a versão antiga (3.5.1 ou anterior); **não desinstale nem apague os dados**.
2. Faça push da `3.5.2`. O workflow compilará e publicará a Release **v3.5.2** automaticamente com `rollout=0` (zero atualização automática).
3. Na **versão antiga**, abra **Sobre → Buscar atualização agora** e selecione **Instalar pelo Android** para aplicar a 3.5.2. Se preferir, baixe o APK assinado direto da Release e abra pelo instalador do Android. **Não espere que o novo código de Shizuku funcione antes de instalar a 3.5.2**: nesse momento ainda está rodando o instalador antigo.
4. Confirme que o coletor já executa a **3.5.2** e que scanner, bipes, Shizuku e preferências continuam funcionando.
5. Para testar o **novo instalador Shizuku**, publique uma versão posterior (por exemplo `3.5.3`, alterando somente `bridgeVersion`), ainda com `rollout=0`. A partir da **3.5.2**, use **Buscar atualização agora → Instalar via Shizuku** para tentar a instalação da 3.5.3. Se o firmware negar a transferência, o Bridge exibirá a etapa/erro e oferecerá **Instalar pelo Android**.
6. Só depois de validar essa atualização de ponta a ponta, edite a descrição da Release escolhida para ampliar o `rollout` gradualmente.

**Limite importante:** as restrições específicas do Android/firmware podem impedir a instalação silenciosa via Shizuku, mesmo com o APK correto. Nesse caso, a alternativa pelo Android requer permissão para instalar apps dessa fonte e confirmação do usuário. O aplicativo não tenta abrir interfaces em segundo plano.

## Gerar e publicar uma atualização

1. Altere **somente** `def bridgeVersion` em `app/build.gradle` para a próxima versão (não reutilize uma tag já lançada).
2. Faça push na branch `main`. O workflow executa testes, assina o APK, publica em Actions → Artifacts e cria automaticamente uma Release pública `vN.N.N` com o APK assinado e `rollout=0`.
3. A Release fica disponível para **detecção e download manual**. Com `rollout=0`, a instalação automática permanece bloqueada intencionalmente.
4. Teste o APK assinado no coletor piloto, verificando o scanner, Shizuku, configuração e instalação. Nunca desinstale a versão anterior como primeira tentativa.
5. Após aprovar, edite a **descrição da mesma Release** para `rollout=10`, `rollout=25`, `rollout=50`, `rollout=100` conforme o progresso.
6. As verificações periódicas do Android não são instantâneas e a instalação em segundo plano exige Shizuku e inatividade comprovada do MLOG.

O workflow também admite execução manual por Actions. Se a Release da versão **já existe**, ele não sobrescreve o APK nem altera o rollout. Não substitua uma tag publicada por um APK diferente.

### Segredos obrigatórios do GitHub Actions

Em **Settings → Secrets and variables → Actions**, configure:

- `BRIDGE_KEYSTORE_BASE64`: conteúdo Base64 da chave corporativa de assinatura (formato PKCS#12).
- `BRIDGE_KEYSTORE_PASSWORD`: senha da chave `seridoplast_bridge`.

A compilação verifica que o certificado da Release tem o SHA-256 fixado no projeto e no workflow; se não corresponder, a publicação falha. **Não coloque a chave nem a senha no repositório.**

## Atualização no coletor

Em **Sobre → Atualizações automáticas**, existem os controles:

- **Verificar atualizações automaticamente**: ativo inicialmente; agendamento de verificação a cada aproximadamente duas horas quando o Android permitir.
- **Instalar quando o coletor estiver inativo**: ativo inicialmente, mas respeita a porcentagem do rollout e as salvaguardas de foco/inatividade.
- **Buscar atualização agora**: baixa e verifica imediatamente a última Release aprovada pelo GitHub.
- **Instalar APK já baixado pelo Shizuku**: comando **manual** para testar a instalação em um coletor piloto, independentemente da porcentagem de rollout.

Se a instalação Shizuku falhar com `EPIPE`, o Bridge apresenta a resposta do gerenciador de pacotes e oferece um botão **Instalar pelo Android**, com confirmação do operador. O modo automático apenas registra a falha; ele não abre uma janela no meio da operação.

### Instalar o APK via USB (somente o primeiro rollout/piloto)

No PowerShell, com o APK assinado em `Downloads`:

```powershell
cd "$env:USERPROFILE\Downloads"
.\platform-tools\adb.exe devices
.\platform-tools\adb.exe install -r ".\MLOG-Scanner-Bridge-v3.5.2.apk"
```

Use sempre `install -r`. Se aparecer erro de assinatura incompatível, **não desinstale automaticamente**: isso apagaria as preferências existentes.

## Diagnóstico do atualizador

1. Em **Sobre**, consulte a mensagem da última verificação/instalação.
2. Confira se a Release está **pública**, publicada (não rascunho/prerelease) e contém o arquivo `MLOG-Scanner-Bridge-vN.N.N.apk` e o digest SHA-256.
3. Verifique o status do Shizuku e a autorização do Bridge.
4. Confirme se o valor na descrição da Release está como `rollout=N` **sozinho em uma linha**. Uma Release sem essa linha equivale a **0%** por segurança.
5. Se o aparelho estiver separando, com MLOG em primeiro plano, o Bridge **deve esperar**; não interrompa a separação para forçar atualização.
6. `pm install-create`, `install-write` e `install-commit` são as etapas de instalação. Em caso de EPIPE, anote a etapa e a resposta `exit=` que aparecerem em Sobre. O Android pode bloquear o shell por política do fabricante.
7. Para investigar erros técnicos, com ADB autorizado: `adb logcat -d -s MLOGBridge:I`.

A atualização automática não substitui MDM nem é infalível: o Android controla horários de execução em segundo plano, e os comandos privilegiados dependem de Shizuku. Primeiro faça a validação em 1 coletor antes de ampliar a liberação.

## Estrutura mínima do repositório

`app/` — Android Java e recursos · `.github/workflows/gerar-apk.yml` — testes e build/Release · `tests/` — testes Java · `branding/` e `assets/` — imagens · `README.md` — documentação.

Não há outros documentos `.md`/`.txt` versionados neste pacote.


### v3.5.6 - recuperacao por Volume na Separacao

O diagnostico do coletor identificou a tela **Separacao**, enquanto a v3.5.5
bloqueava a recuperacao por permitir apenas Conferencia de Venda. A rotina
Volume (648,175) -> 10 ms -> seta (55,103) agora aceita **Separacao** somente
quando a arvore de acessibilidade confirma o titulo, os controles da separacao,
o rotulo Volume e o botao de cabecalho na posicao homologada (720x1440).
Mantidos Shizuku, assinatura corporativa, instalacao e rollout.

**Importante:** o acionamento automatico atual e o fechamento do teclado;
nao ha comprovacao universal de leitura ignorada pelo MLOG. Use Resetar Bipe
para acionar manualmente se a falha ocorrer sem evento do teclado.
