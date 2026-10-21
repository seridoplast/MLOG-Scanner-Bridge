# MLOG Scanner Bridge v0.3 — foco inteligente (protótipo)

Projeto **Android/Java** para o coletor CMX e o MLOG (`com.targetsistemas.target_mlog2`). **Nao e um APK pronto**: o GitHub Actions compila `app-debug.apk` para testes. **Nao foi validado no coletor fisico nem na arvore de acessibilidade real do MLOG Flutter.**

## Motivacao — os dois layouts enviados

No modulo **Separacao**, a tela de item comum apresenta os icones Camera + Teclado. Em itens fracionados/por quantidade ha ainda o botao **Quantidade: 1**. Um mapeamento fixo por posicao de tela acaba clicando em botoes errados. Nas telas de notas o icone de teclado pode aparecer em lugar diferente.

## O que a v0.3 faz

1. Mantem o **IME invisivel** da v0.2, que usa `InputConnection.commitText` somente no MLOG.
2. Adiciona um `AccessibilityService` opcional, registrado SOMENTE para o pacote do MLOG, que consulta os controles de interface e tenta clicar no botao de teclado usando `ACTION_CLICK` (**sem tocar em coordenadas absolutas**).
3. Pode pre-armar o campo na tela *Separacao* se o MLOG expuser o controle de teclado com descricao compreensivel.
4. Se o scanner entregar um codigo mas ainda nao houver campo conectado, tenta abrir o teclado do MLOG e aguarda ate 1,9 segundo para entregar aquele codigo; senao o descarta sem alterar pedido.
5. Possui um **fallback de layout relativo** somente para *Separacao*; opcional, **DESLIGADO por padrao**. Usa a linha abaixo de `Cod. Fabric`, compara controles clicaveis e escolhe o segundo, apenas quando a linha e inequivoca. Nao serve como identificador geral de todos os icones do MLOG.
6. Se a acessibilidade estiver desativada, pode funcionar como v0.2, com toque manual no campo (configuracao de compatibilidade inicialmente DESLIGADA para evitar inserir codigos em Quantidade).
7. Diagnostico no proprio app com situacao do servico, numero de controles visiveis e se encontrou teclado / quantidade. **Nao registra o conteudo do produto, numero do pedido ou codigo de barras**.
8. Os formatos do scanner continuam configuraveis, mas dependem do decodificador fisico.

## Limites e protecoes deliberadas

- Nao altera, recompila ou substitui o MLOG original.
- Nao pede permissao de internet, contatos, arquivos, gravacao de tela, captura de teclas ou screenshots.
- Somente eventos do pacote `com.targetsistemas.target_mlog2` entram no servico de acessibilidade.
- A acessibilidade usa consulta de nos e `ACTION_CLICK`: **nao requisita gestos nem intercepta botoes fisicos**.
- O IME valida `EditorInfo.packageName` e recusa senhas e editores rotulados como quantidade/peso/volume; tambem bloqueia campo numerico sem identificacao de codigo. Esses controles podem ser insuficientes se o MLOG nao publicar rotulos apropriados.
- **Enter automatico OFF** e **fallback de layout OFF** por padrao. Primeiro teste deve ocorrer em pedido de homologacao.
- Codigos transitam somente pela memoria do processo, com expiração curta. Nao sao gravados em arquivos, preferencias ou logs.
- Os broadcasts do firmware CMX nao parecem exigir permissao criptografica de origem. **Um aplicativo malicioso no mesmo Android poderia falsificar um broadcast**. Nao classificar como aplicativo de alta garantia ate validar o firmware e possivelmente implementar verificacao de origem/assinatura do emissor.
- No MLOG Flutter, a arvore de acessibilidade pode nao publicar o botao de teclado ou campo de texto. Nesse caso nao ha como garantir foco automatico; o diagnostico vai mostrar a ausencia e o app falhara de modo conservador.
- Os prints da Separacao mostram icones, mas **nao comprovam** os nomes das opcoes no `AccessibilityNodeInfo`. A tela de notas ainda nao tem captura de arvore de acessibilidade.
- App instalavel por APK com acessibilidade **pode ser restringido pelo Android/Play Protect mesmo com codigo correto**. Nao ha solucao legitima via alteracao de manifesto para garantir ausencia de alerta. Use distribuicao gerenciada (Google Play privado/MDM) e aprovacao do administrador se necessario.
- Builds `debug` do GitHub Actions nao sao versoes de producao. Configure **assinatura release persistente** e processo de atualizacao antes de implantar em varios coletores. Nao envie chave privada ao repositorio.

## Instalar pelo GitHub Actions (sem Android Studio)

1. Extraia este ZIP na **raiz** de seu checkout `Downloads\MLOG-Scanner-Bridge`, sobrescrevendo os arquivos antigos. **Nao crie uma pasta dentro do repo**.
2. Exclua dois arquivos antigos v0.1 se ainda existirem:
   - `app/src/main/java/br/com/seridoplast/mlogbridge/MlogAccessibilityService.java`
   - `app/src/main/res/xml/accessibility_config.xml`
3. Dentro do checkout, execute:

```cmd
git add -A
git commit -m "Bridge v3 foco inteligente"
git push origin main
```

4. Abra `https://github.com/seridoplast/MLOG-Scanner-Bridge/actions`. Quando a nova execucao tiver sucesso, abra **Artifacts → MLOG-Scanner-Bridge-v3-APK** e extraia `app-debug.apk`.
5. Como o runner assina APK `debug` usando chave temporaria, talvez seja preciso desinstalar o prototipo anterior antes de instalar o novo. Essa desinstalacao apaga as preferencias do Bridge (nao do MLOG).
6. **Reative o Play Protect** se estiver desligado. Se o Android recusar o app ou a acessibilidade, nao tente contornar a protecao; distribua por um canal administrado e autorizado. A permissao so pode ser concedida pelo usuario/administrador, nao pelo proprio app.

## Testes minimos obrigatorios (antes de producao)

1. Confira que o CMX envia o codigo na tela de teste do Bridge.
2. Ative o teclado especial e selecione-o como teclado atual. Nao o use para login ou senha.
3. Tente habilitar `MLOG Bridge - foco inteligente` em Acessibilidade. Se o Android nao oferecer essa opcao, nao podemos resolver o bloqueio apenas mudando a API do projeto.
4. No Bridge, marque `Ativar as leituras assistidas` e deixe **Enter OFF** e **layout relativo OFF**.
5. Abra um pedido de teste na Separacao, tire um print do diagnostico no Bridge: `Separacao: N botoes acessiveis; teclado rotulado/nao identificado; quantidade visivel/ausente`.
6. Teste um item comum (sem Quantidade), depois um item fracionado (com `Quantidade: 1`), **conferindo que o codigo NAO entra no campo de quantidade**.
7. Teste telas de notas de entrada com o botao do teclado em outro lugar.
8. Se a arvore de acessibilidade nao identificar o teclado, **pare** e envie o diagnostico. So considere ativar o fallback relativo na Separacao, com pedido de teste, se houver dois controles alinhados; nunca ative em operacao sem homologacao.
9. So depois dos testes avalie ativar Enter automatico, com verificacao das confirmacoes do MLOG.

## Compilacao

Gradle 8.11.1 / Android Gradle Plugin 8.9.1 / Java 17 / Android SDK 35. O workflow nao usa `sdkmanager --licenses` interativo. Este pacote passou por revisao estatica e verificacao de XML/arquivos, **nao por compilacao em Android SDK real** neste ambiente.
