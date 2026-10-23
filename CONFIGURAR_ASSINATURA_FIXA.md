# MLOG Scanner Bridge — assinatura fixa (v3.0.7)

Esta versao usa um **APK release assinado** sempre pela MESMA chave. A chave privada NAO esta incluída neste projeto. O GitHub Actions so consegue compilar o APK de producao depois de configurar os dois GitHub Secrets abaixo.

## 1. Guardar os arquivos secretos (uma unica vez)

Baixe o pacote de chave privada fornecido separadamente da fonte. Ele contem:

- `seridoplast-bridge-release.jks` — chave de producao, que precisa ser guardada em backup privado.
- `BRIDGE_KEYSTORE_BASE64.txt` — codificacao da mesma chave para colocar como GitHub Secret.

Baixe separadamente `Senha_Assinatura_Bridge_GUARDAR.txt`, que contem a senha da chave. **Nunca coloque esses arquivos no Git, no ZIP de codigo-fonte ou em mensagens publicas**. Armazene a chave e sua senha em backup seguro (preferencialmente separados). Sem a chave original, nao ha como assinar uma atualizacao compativel com ela.

## 2. Configurar no repositorio GitHub (uma unica vez)

Abra: `https://github.com/seridoplast/MLOG-Scanner-Bridge/settings/secrets/actions`

Entre em **New repository secret** e crie exatamente:

1. `BRIDGE_KEYSTORE_BASE64` — cole todo o conteudo do arquivo `BRIDGE_KEYSTORE_BASE64.txt` (uma unica linha, sem espacos extras).
2. `BRIDGE_KEYSTORE_PASSWORD` — cole o conteudo do arquivo `Senha_Assinatura_Bridge_GUARDAR.txt` (apenas a senha).

O `keyAlias` ja esta fixo no codigo: `seridoplast_bridge`. O workflow rejeita secrets ausentes ou errados e verifica o certificado SHA-256 ANTES de publicar o APK.

**Importante:** a chave deve ser configurada como **secret** e nao como variável comum do repositorio.

## 3. Enviar a v3.0.7 ao GitHub

Execute o arquivo `ATUALIZAR_PELO_CMD.txt`, ou copie seus comandos para o CMD do Windows. Ao dar `git push`, a build será disparada. Se o push tiver sido feito antes de configurar os secrets, a primeira build falhara com erro explicativo: termine de cadastrar os secrets e depois abra **Actions > Gerar APK do MLOG Scanner Bridge > Run workflow**.

Na execução verde, baixe o artifact **MLOG-Scanner-Bridge-v307-APK-ASSINADO**. O nome do arquivo agora e **`app-release.apk`** (nao mais `app-debug.apk`).

## 4. Migrar o coletor uma unica vez

Os APKs antigos foram gerados com chaves de debug diferentes. Por isso, o primeiro APK com esta chave nova **NAO consegue atualizar por cima** do APK debug antigo sem assinatura compativel.

Para um coletor com versao antiga:

```bat
"%USERPROFILE%\Downloads\platform-tools\adb.exe" uninstall br.com.seridoplast.mlogbridge
"%USERPROFILE%\Downloads\platform-tools\adb.exe" install "%USERPROFILE%\Downloads\app-release.apk"
```

**Cuidado:** `uninstall` apaga configuracoes e dados locais do Bridge. Registre valores e preferencias antes, confira acessibilidade, selecao do teclado e bolha flutuante apos instalar. Nao desinstala o MLOG da Target.

Para a **proxima** atualizacao e todas as seguintes que usem a MESMA chave:

```bat
"%USERPROFILE%\Downloads\platform-tools\adb.exe" install -r "%USERPROFILE%\Downloads\app-release.apk"
```

O Android mantem os dados do Bridge quando a atualizacao por cima e concluida com sucesso. Verifique sempre a instalacao em um coletor de teste antes de distribuir para todos.

## 5. Como funciona o versionCode

A compilacao no GitHub calcula automaticamente `versionCode = 100000 + GITHUB_RUN_NUMBER`. Assim, novas execucoes desse workflow recebem numeros superiores, adequados para distribuicao por MDM. **Nao crie um segundo workflow com contagem independente ou troque de chave** sem planejar migracao.

## 6. Conferir a assinatura do APK

O workflow verifica a assinatura pelo `apksigner` e compara com o certificado corporativo fixo:

`81:51:37:6D:E3:3A:BD:EE:4C:98:CD:A4:C8:8C:10:2A:8B:91:65:A0:DB:7E:DC:18:B1:AD:04:03:A7:22:2F:AE`

Esse fingerprint e publico (nao e a chave privada). Se a chave for substituida acidentalmente, o workflow nao publica um APK incompativel.

**Seguranca:** nunca publique a chave `.jks` ou arquivos de senha no repositório; nao repita a geracao de chave para cada APK.
