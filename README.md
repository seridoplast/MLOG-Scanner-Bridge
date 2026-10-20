# MLOG Scanner Bridge — protótipo experimental

Projeto de teste Android para o coletor **CMX Supply** e o **MLOG 9.3.5**.

- Código obtido a partir da análise dos dois APKs que o usuário forneceu.
- Configuração do scanner para enviar leitura por broadcast e habilitar simbologias.
- Tentativa de identificar, focar e preencher o campo de código do MLOG por acessibilidade.
- Não altera nenhum dos APKs originais.
- Sem testes no hardware e sem garantia de compatibilidade com a UI Flutter do MLOG.

**Como gerar APK sem Android Studio:** consulte [COMO_GERAR_APK_SEM_ANDROID_STUDIO.txt](COMO_GERAR_APK_SEM_ANDROID_STUDIO.txt). O GitHub Actions fará a compilação.

**Avisos:** não utilize em pedidos reais antes de testar. O modo broadcast pode mudar as configurações do leitor, e o serviço de acessibilidade terá acesso à janela ativa do MLOG. O código-fonte não implementa tráfego de rede.
