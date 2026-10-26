# v3.2.7 — Modos de bipe e preparacao do teclado

## Motivacao e diagnostico

A v3.2.6 nao abria necessariamente o teclado porque o teclado selecionado e um
campo focado sao coisas diferentes. O Android so entrega InputConnection ao IME
quando um editor do MLOG recebe foco. Um broadcast com scanner decodificado
nao concede foco automaticamente em outro aplicativo.

## Ajustes

Agora aparecem dois cartoes mutuamente exclusivos:

* **Bipe antigo**: leitor nativo + foco e Enter pela acessibilidade; opcoes de
  leitura assistida, deteccao de botao, toques guiados, Enter e gatilho longo.
* **Bipe Zebra**: leitor nativo + entrega por InputConnection do teclado Bridge;
  opcoes de Enter, texto em bloco ou caracteres como eventos, barra visual,
  foco assistido opcional e fallback de EditorAction.

Tocar no cartao ativo abre/fecha seus ajustes. Ativar o outro modo encerra a fila
anterior e troca a responsabilidade pelo codigo e pelo Enter; nunca ficam os
dois responsaveis operacionais simultaneamente.

## Inicializacao do modo Zebra

1. Ative Bipe Zebra no Bridge.
2. Em Ajustes, habilite **MLOG Bridge - teclado experimental** no Android.
3. Selecione esse teclado como IME padrao pelo seletor do Android.
4. Abra o MLOG numa conferencia ou separacao de TESTE.
5. Toque no **icone de teclado do MLOG** para obter um campo de codigo ativo.
6. Com a barra Bridge aberta, teste um unico bipe, depois 3 bipes. Confira
   pessoalmente as quantidades registradas, nao so o log de Enter.

### Abertura automatica opcional do campo

Se a acessibilidade do Bridge estiver ativa e **Abrir campo pelo Bridge**
estiver marcado, o Bridge tenta clicar SOMENTE num editor ou icone de teclado
semantico reconhecido do MLOG. Um primeiro bipe pode ser temporariamente
retido por ate 900 ms durante a inicializacao do IME. Sem acessibilidade,
use o icone de teclado manualmente pelo menos uma vez. Nao existem toques
inseguros por coordenadas nesse modo.

Em coletores com teclado fisico, o IME agora solicita a exibicao de uma barra
compacta quando existe InputConnection viva. Essa barra nao representa
conexao garantida: em Ajustes, confira o campo **Campo do MLOG: conectado**.

## Metodos de entrega de caracteres

Por padrao, a palavra completa vai em `InputConnection.commitText()` e o
Enter em `sendKeyEvent(DOWN/UP)`; modo mais rapido e proximo da configuracao
DataWedge de teclado com caracteres como string e Enter como tecla.

A opcao *Caracteres como teclas* envia eventos individuais usando
`KeyCharacterMap`. E experimental; pode ser mais lenta, mas ajuda a testar
firmwares ou campos Flutter com comportamento diferente.

O MLOG pode nao processar uma tecla aceita pelo Android. Nao declaramos
produto conferido a partir de `sendKeyEvent` sozinho.

## Limites

* O diagnostico Zebra disponivel nao mostra as configuracoes exatas do perfil
  DataWedge do MLOG; isto nao comprova replicacao perfeita do fabricante.
* A versao nao altera codigo-fonte do MLOG ou o SDK/driver do Zebra.
* O servico nativo CMX/Hikrobot precisa estar instalado.
* Leitura sem IME selecionado ou sem foco nao pode ser injetada com seguranca.
* Quando trocar de modo, verificacoes e fila anteriores sao invalidadas para
  nao enviar leitura atrasada no modo novo.
* Apos instalar nova APK pelo instalador Android, pode ser necessario reativar
  acessibilidade por Configuracoes restritas. O modo Zebra pode funcionar sem
  acessibilidade, depois que o campo do MLOG esta manualmente focado.

## Diagnostico via ADB

Com o coletor ligado:

```powershell
cd "$env:USERPROFILE\Downloads"
.\platform-tools\adb.exe logcat -c
.\platform-tools\adb.exe logcat -s MLOGBridge:I
```

Procure por `ZEBRA_IME_NO_MLOG_INPUT_IGNORED`,
`ZEBRA_IME_OPENING_MLOG_EDITOR_WITH_OPTIONAL_ACCESSIBILITY`,
`ZEBRA_IME_SCAN_QUEUED_DEPTH_`, `ZEBRA_IME_CODE_COMMITTED_MS_`,
`ZEBRA_IME_ENTER_SENT_UNVERIFIED` e `ZEBRA_IME_INPUT_NOT_ACTIVE`.

## Testes e publicacao

Os testes Java puros sao executados no workflow. Compilacao Android e
validacao no firmware do coletor devem ser realizadas via GitHub Actions.
`app/build.gradle` publica a versao `3.2.7`; o workflow de Release permanece
com assinatura corporativa e tag `v3.2.7`.
