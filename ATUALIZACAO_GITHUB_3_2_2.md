# MLOG Scanner Bridge v3.2.2 — botão de atualização GitHub

## Fluxo
1. **Sobre → Buscar atualização no GitHub** consulta a última Release estável do repositório `seridoplast/MLOG-Scanner-Bridge`.
2. `bridge-build-N` corresponde ao `versionCode=100000+N` definido no GitHub Actions. Builds com versão igual ou anterior não são instaladas.
3. O app baixa **somente** `app-release.apk` da Release vinculada ao repositório, por HTTPS.
4. Verifica o **SHA-256** publicado pelo GitHub, o identificador `br.com.seridoplast.mlogbridge`, o código da versão e o SHA-256 do certificado corporativo `8151376de33abdee4c98cda4c88c102a8b9165a0db7edc18b1ad0403a7222fae`.
5. O Android solicita permissão de fontes desconhecidas, se necessária, e a confirmação da atualização. Nada é instalado silenciosamente.

## Publicação
- O workflow continua publicando o artifact temporário como antes.
- Para **repositórios públicos**, após validar o APK o workflow também cria uma **Release** com tag `bridge-build-${GITHUB_RUN_NUMBER}`, disponibilizando o APK assinado.
- Para **repositórios privados**, não publica automaticamente uma Release acessível sem login. **Não colocar token pessoal de acesso ao GitHub no APK.** Nesses casos, utilizar o servidor local já previsto em `BridgeUpdater` ou configurar distribuição autenticada no backend da empresa.
- O build mantém o mesmo applicationId, serviço de acessibilidade e certificado de assinatura. Em atualizações comuns os dados/permissões persistem, mas alguns firmwares exigem reativar a acessibilidade manualmente.

## Teste obrigatório antes da operação
1. Confirme que a Release foi publicada e o endpoint `https://api.github.com/repos/seridoplast/MLOG-Scanner-Bridge/releases/latest` responde 200; 404 geralmente indica repositório privado ou nenhuma Release.
2. Num **único coletor de teste**, abra Sobre → Buscar atualização no GitHub; confirme o APK baixado e a versão identificada.
3. Toque Instalar, autorize instalação por esse app se o Android pedir, e **não desinstale a versão anterior**.
4. Verifique que Ajustes, acessibilidade, teclado Bridge, botão flutuante e controle nativo mantiveram os valores após atualização. Verifique uma leitura real em pedido de treinamento.
5. Se o Android desligar acessibilidade por política de segurança, reative manualmente em Configurações → Acessibilidade.

Esta revisão acrescenta exclusivamente o fluxo de atualização. Não altera o mecanismo de foco, leitura ou Enter da v3.2.1. Ainda precisa ser compilada no GitHub Actions e homologada em um coletor físico.
