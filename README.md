# MLOG Scanner Bridge v0.5 — leitura direta, sem teclado próprio

Projeto Android para compilação automatizada pelo GitHub Actions. **Protótipo não homologado no coletor CMX / no MLOG da Target.**

## Mudanças em relação à v0.4

- Retira completamente o InputMethodService e o teclado invisível. O teclado normal (Gboard ou outro) continua selecionado.
- Acessibilidade limitada ao pacote `com.targetsistemas.target_mlog2`.
- Scanner envia código através de broadcast (motor nativo CMX/Hikrobotics).
- No momento da leitura, serviço procura campo editável de código claramente identificado. Se ainda não existir, procura botão de teclado e tenta acioná-lo por ação de acessibilidade.
- Após abrir editor, tenta `AccessibilityNodeInfo.ACTION_SET_TEXT`, **sem mudar o teclado padrão**.
- `SoftKeyboardController.SHOW_MODE_HIDDEN` pode ocultar temporariamente o teclado do Android, restaurando `SHOW_MODE_AUTO` em até 1,6 s ou em eventos de quantidade/peso. O Android não garante que todo IME cumpra a solicitação.
- Telas identificadas: separação, notas (quando legendadas) e “Bipar Atividades de Separação”. A semântica do Flutter pode não disponibilizar título, botão ou editor: nesse caso, o aplicativo não insere nada e informa falha no diagnóstico.
- Modo escuro/claro/automático da v0.4 mantido.
- Enter automático **desativado** por padrão. Quando habilitado, usa ACTION_IME_ENTER somente se a própria tela oferecer essa ação; não tenta clicar automaticamente em “Iniciar”.
- Tentativa pelo layout relativo **desativada** por padrão; é uma heurística experimental e deve ser testada manualmente antes de habilitar.

## Segurança

- Não solicita `android.permission.INTERNET`.
- Não armazena códigos lidos em disco nem em diagnósticos; só armazena estado de funcionalidades e mensagens sem o conteúdo do código.
- Não usa captura de tela, clipboard, injeção ADB ou filtros globais de teclado.
- O broadcast enviado pelo hardware não está autenticado criptograficamente: outro aplicativo com permissão para enviar broadcast equivalente pode tentar forjar uma leitura. Por esse motivo, teste antes de empregar em pedidos reais e controle os aplicativos instalados nos coletores.
- Só responde quando a janela principal do MLOG está visível/ativa e quando reconhece uma das telas permitidas.
- Campos de quantidade/peso/senha são barrados por rótulo, mas **campos sem rótulos adequados no Flutter exigem validação real**; não é possível provar correspondência apenas a partir de prints.
- O modo de tela relativa é opcional e pode selecionar um botão errado se o Flutter não expuser os nós adequadamente.
- Não oculta Play Protect nem muda configurações restritas de Android. Usuário deve autorizar a acessibilidade manualmente ou por procedimento corporativo permitido.

## Compilação e instalação

1. Atualize os arquivos na raiz do repositório, removendo `ScannerInputMethodService.java` e `app/src/main/res/xml/input_method.xml` antigos.
2. `git add -A && git commit -m "v0.5 leitura direta" && git push origin main`.
3. GitHub Actions > `Gerar APK do MLOG Scanner Bridge` > execução verde > artefato `MLOG-Scanner-Bridge-v5-APK`.
4. Para instalar em um dispositivo de teste com USB debugging, use Android SDK Platform Tools `adb install -r caminho\app-debug.apk`. **Se houver `INSTALL_FAILED_UPDATE_INCOMPATIBLE`**, a assinatura debug mudou; antes de desinstalar, anote configurações, e faça a desinstalação somente após avaliar o impacto.
5. Ative o serviço de acessibilidade `MLOG Bridge - foco inteligente`, selecione **Gboard** (não existe mais teclado Bridge), abra o app e marque **Ativar as leituras assistidas**.
6. Confirme que o CMX envia leituras por broadcast. Teste primeiro separação comum, bobina com quantidade, atividades e nota, sem lançar dados reais.
7. Se não entrar código, abra o aplicativo e anote a linha Diagnóstico: tela, editores, botões, ações disponíveis. Precisamos de um dump de acessibilidade real para tratar elementos Flutter que não são publicados.

### Conflitos CMX

Dois serviços de acessibilidade tentando atuar no mesmo campo podem conflitar. Se o **serviço de acessibilidade do aplicativo CMX Scanner** não for necessário para o scanner transmitir código via broadcast, desative **somente esse serviço**, conservando instalado o aplicativo/serviço de hardware CMX. Teste o scanner na tela de diagnóstico primeiro. Não desative acessibilidade do Bridge.

### Escalonamento para 30 coletores

Para testes em alguns aparelhos: instale via `adb` em cada unidade autorizada e conectada. Para operação empresarial: use Android Enterprise (MDM/UEM, Managed Google Play privado) e **assine versões de produção com uma chave de assinatura estável protegida fora do repositório**, sem depender de assinaturas debug aleatórias geradas pelo GitHub Actions. Instalar pela Google Drive/Files pode voltar a gerar restrições de acessibilidade no Android 14. A gestão de acessibilidade depende das políticas aplicáveis e não é automaticamente autorizada pela instalação gerenciada.

## Limites funcionais

- O Bridge pode impedir que o teclado **do Android** apareça temporariamente, mas não consegue ocultar componentes próprios (diálogos/formulários) que o MLOG exibir por dentro do Flutter.
- O botão físico de gatilho inicia a leitura no serviço nativo do coletor; este aplicativo responde ao **resultado decodificado**, não intercepta o key-event físico (para evitar conflito).
- O app não determina quando o pedido foi gravado ou confirmado; o sucesso de `ACTION_SET_TEXT` não garante confirmação no MLOG.
- Compile e valide no equipamento antes de usar em produção.
