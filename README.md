# MLOG Scanner Bridge — Seridóplast

Aplicativo Android para os coletores CMX TC60 (Android 14), integrando o scanner físico ao **MLOG** por broadcast e, no modo escolhido pelo operador, comandos pelo **Shizuku**.

**Versão do projeto:** `3.5.1` · **Repositório:** `seridoplast/MLOG-Scanner-Bridge` · **Pacote:** `br.com.seridoplast.mlogbridge`.

## Funcionalidades

- Leitura do scanner nativo CMX por **broadcast exclusivo**; modos **Bipe Shizuku** e **Bipe Normal**.
- Botão flutuante **Resetar Bipe**, recuperação experimental pela câmera e controle protegido de bipes automáticos.
- Alerta quando o Shizuku não estiver disponível.
- **Atualizador corporativo:** verifica o GitHub Releases, baixa APK de produção por HTTPS, exige SHA-256 idêntico ao digest informado pelo GitHub, confirma pacote, versão superior e certificado da chave corporativa.
- **Instalação pelo Shizuku:** transmite o APK diretamente para `pm install -r -S` por `stdin`, sem exigir que o shell acesse o diretório privado do Bridge.
- **Distribuição gradual:** uma Release publicada com `rollout=0` não instala nada automaticamente. É possível ampliar a liberação para `rollout=10`, `rollout=25`, `rollout=50` e `rollout=100` editando a descrição da mesma Release.

## Pré-requisitos

1. Coletores na internet (GitHub API e download de Releases precisam estar acessíveis).
2. APKs assinados **sempre com a mesma chave corporativa**. Um APK com outra assinatura será rejeitado.
3. Shizuku **iniciado e autorizado** para instalação pelo shell. Se estiver desligado, o APK pode ser baixado, mas a instalação automática fica pendente.
4. O aplicativo MLOG deve ficar fora da tela de operação durante a instalação. O Bridge só tenta atualizar quando a tela está apagada ou quando uma tela de outro aplicativo é identificada de modo conservador, após pelo menos cinco minutos sem leituras Shizuku. Se não conseguir verificar que está livre, adia.

O Shizuku precisa de inicialização adequada após reinicializações. O atualizador **não consegue iniciar o serviço Shizuku sozinho**. A instalação silenciosa depende também do comportamento do firmware CMX, que deve ser validado no aparelho piloto.

## Teste da detecção — v3.5.1

**Alterações em relação à v3.5.0:** somente o nome visível do aplicativo mudou para **MLOG Bridge TESTE**, e a versão Android avançou de **3.5.0** para **3.5.1** (`versionCode` 1.030.500 → 1.030.501). O identificador `br.com.seridoplast.mlogbridge`, o mecanismo de atualização e a assinatura corporativa foram preservados.

Para validar a detecção sem atualizar todos os coletores:

1. Instale **v3.5.0** em **um coletor piloto** e confirme que a consulta a GitHub Releases funciona.
2. Envie **v3.5.1** ao GitHub; confirme que a compilação em Actions está verde.
3. Em **Actions → Gerar APK do MLOG Scanner Bridge → Run workflow**, habilite `publish_release` e escolha **`rollout_percent = 0`**. A Release **v3.5.1** precisa ser publicada, não apenas compilada. O GitHub Actions criará o APK assinado na Release.
4. No coletor **ainda com v3.5.0**, abra **Bridge → Sobre → Buscar atualização agora**. Deve aparecer **`v3.5.1`** como atualização assinada, com `rollout 0%`.
5. Para testar a **instalação manual pelo Shizuku**, feche a Separação, toque em **Instalar via Shizuku** e confirme; depois confira se o nome do ícone mudou para **MLOG Bridge TESTE**.
6. **Não mude o rollout para 100% durante o teste**, salvo se tiver certeza de que somente coletores de laboratório possuem a v3.5.0 e estão aptos a receber atualizações. O modo automático precisa de Shizuku ativo e inatividade, e é agendado pelo Android, portanto não é instantâneo.

Se o Bridge indicar que não há atualização, confirme que a Release v3.5.1 está publicada como estável (sem marcação de prerelease), é a última Release e contém o APK no nome padrão. Com `rollout=0`, a detecção e o download manual funcionam; a instalação automática fica bloqueada intencionalmente.

## Gerar e publicar uma atualização

1. Aumente a versão no `app/build.gradle`: `def bridgeVersion = '3.5.1'` → uma versão maior, por exemplo `3.5.2` (não reutilize a mesma tag para um APK novo).
2. Envie os arquivos para o branch `main`. Todo push compila e produz o APK assinado em **Actions → Artifacts**, sem publicar automaticamente um Release.
3. Baixe e instale o APK do Artifact **somente em um coletor de testes**. Valide leitura, Shizuku, abrir e fechar teclado, Resetar Bipe, bipes automáticos, reinicialização e atualização.
4. Quando o piloto estiver aprovado, vá a **Actions → Gerar APK do MLOG Scanner Bridge → Run workflow**, marque `publish_release` e deixe `rollout_percent = 0` inicialmente. Isso cria a Release `vN.N.N` com o APK assinado e a descrição `rollout=0`.
5. Teste o botão de atualização manual em **Sobre → Atualizações automáticas**, usando o Shizuku no piloto.
6. Edite a **descrição da Release** no GitHub: troque **apenas a linha** `rollout=0` por `rollout=10` e depois por `25`, `50` e `100` conforme a validação. **Não substitua o APK** de uma Release já distribuída.
7. Para atualizar todos os coletores aprovados, deixe `rollout=100`. Eles consultarão a Release periodicamente e instalarão quando houver Shizuku, conectividade e condição de inatividade.

**Importante:** mudar a porcentagem **não exige compilar outro APK**. O Bridge usa um grupo estável por coletor e não envia identificadores do aparelho ao GitHub. O agendamento periódico é aproximado; restrições de bateria e Wi-Fi do Android podem atrasá-lo.

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

Caso a instalação Shizuku não funcione, o fluxo manual ainda permite abrir o **instalador padrão do Android**, que poderá exigir autorização e confirmação do operador.

### Instalar o APK via USB (somente o primeiro rollout/piloto)

No PowerShell, com o APK assinado em `Downloads`:

```powershell
cd "$env:USERPROFILE\Downloads"
.\platform-tools\adb.exe devices
.\platform-tools\adb.exe install -r ".\MLOG-Scanner-Bridge-v3.5.1.apk"
```

Use sempre `install -r`. Se aparecer erro de assinatura incompatível, **não desinstale automaticamente**: isso apagaria as preferências existentes.

## Diagnóstico do atualizador

1. Em **Sobre**, consulte a mensagem da última verificação/instalação.
2. Confira se a Release está **pública**, publicada (não rascunho/prerelease) e contém o arquivo `MLOG-Scanner-Bridge-vN.N.N.apk` e o digest SHA-256.
3. Verifique o status do Shizuku e a autorização do Bridge.
4. Confirme se o valor na descrição da Release está como `rollout=N` **sozinho em uma linha**. Uma Release sem essa linha equivale a **0%** por segurança.
5. Se o aparelho estiver separando, com MLOG em primeiro plano, o Bridge **deve esperar**; não interrompa a separação para forçar atualização.
6. Para investigar erros técnicos, com ADB autorizado: `adb logcat -d -s MLOGBridge:I`.

A atualização automática não substitui MDM nem é infalível: o Android controla horários de execução em segundo plano, e os comandos privilegiados dependem de Shizuku. Primeiro faça a validação em 1 coletor antes de ampliar a liberação.

## Estrutura mínima do repositório

`app/` — Android Java e recursos · `.github/workflows/gerar-apk.yml` — testes e build/Release · `tests/` — testes Java · `branding/` e `assets/` — imagens · `README.md` — documentação.

Não há outros documentos `.md`/`.txt` versionados neste pacote.
