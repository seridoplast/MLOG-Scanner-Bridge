# MLOG Scanner Bridge v1.9 — teclado compacto experimental

## Novidade

Esta versao **nao altera o comportamento da v1.8 por padrao**. O modo novo fica desativado ate a selecao explicita no aplicativo e nas configuracoes Android.

O Bridge ganhou um `InputMethodService` proprio. Quando selecionado como teclado Android, mostra uma **barra compacta** no campo do MLOG (nao o teclado numerico gigante). Ele so envia Enter quando o servico de acessibilidade reconhece **um bipe valido no campo do MLOG** e o codigo ja foi inserido. O CMX permanece responsavel pela leitura.

Para digitacao manual no MLOG, toque em **Digitar** na pequena barra para exibir as teclas e em **Ocultar teclas** para recolher. Fora do MLOG, mostra teclas manuais por padrao. O botao **⇄** abre o seletor de teclado para voltar ao Gboard.

### LIMITACAO REAL DO ANDROID

O Android **nao permite** que um aplicativo comum troque automaticamente o teclado padrao Gboard → Bridge → Gboard apenas ao apertar o gatilho, sem uma autorizacao especial do sistema. Por isso, o modo experimental requer que o usuario **habilite e selecione o teclado Bridge uma vez**. Enquanto estiver selecionado, ele se torna o metodo de entrada padrao ate o usuario voltar a escolher Gboard. Isso NAO e uma alternancia secreta ou automatica.

O teclado Bridge pode acessar o texto do campo ativo, como qualquer outro teclado instalado. Este projeto nao usa internet, nao copia o texto para historico, nao armazena senhas e nao registra codigos em log. So use apos revisar e confiar no APK compilado por voce.

## Como ativar o experimento

1. Instale v1.9 usando ADB. Abra Bridge → **Ajustes**.
2. Ative **Leitura assistida** e **Usar teclado Bridge no Enter**.
3. Toque **1. Habilitar teclado no Android** e habilite o teclado experimental. O Android mostrara aviso de seguranca sobre teclados de terceiros; avalie antes de aceitar.
4. Toque **2. Selecionar teclado / voltar ao Gboard** e escolha **MLOG Bridge - teclado experimental**.
5. Abra o MLOG com acessibilidade do Bridge ligada e teste com um item de demonstracao. O Enter deve ser tentado via `InputConnection` do teclado Bridge sem tocar no Gboard.
6. **Voltar ao modo v1.8:** desmarque `Usar teclado Bridge no Enter` e selecione Gboard pelo seletor. A v1.8 continua presente como fallback sem reinstalar.

## Fluxo

CMX transmite leitura por broadcast → Bridge acessibilidade abre/seleciona campo nativo do MLOG → `ACTION_SET_TEXT` insere codigo → quando o mesmo codigo ainda esta no campo, teclado Bridge tenta `KEYCODE_ENTER` usando `InputConnection` → aguarda mudanca do campo. Se teclado Bridge nao estiver ativo ou nao houver conexao, permanece a logica v1.8 Android 14/Gboard.

O teclado do Bridge **nao substitui a captura fisica do scanner** e nao altera formatos/Enter/configuracoes do CMX. Para evitar duplo Enter, deixe apenas UM aplicativo responsavel por confirmar o codigo durante o teste.

## GitHub pelo CMD

Baixe `MLOG_Scanner_Bridge_v19_Teclado_Experimental.zip` para Downloads, depois:

```bat
cd /d "%USERPROFILE%\Downloads\MLOG-Scanner-Bridge"
powershell -NoProfile -Command "Expand-Archive -LiteralPath (Join-Path $env:USERPROFILE 'Downloads\MLOG_Scanner_Bridge_v19_Teclado_Experimental.zip') -DestinationPath . -Force"
git add -A
git commit -m "MLOG Bridge v19 teclado experimental"
git push origin main
```

GitHub Actions: `MLOG-Scanner-Bridge-v19-APK` → extraia `app-debug.apk` para Downloads.

## Instalar via ADB (depuracao USB)

```bat
"%USERPROFILE%\Downloads\platform-tools\adb.exe" devices
"%USERPROFILE%\Downloads\platform-tools\adb.exe" install -r "%USERPROFILE%\Downloads\app-debug.apk"
```

Se `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, a chave de assinatura gerada no runner mudou. Nao desinstale cegamente: faça backup das configuracoes e confirme se deseja substituir o app de teste.

## Diagnostico

```bat
"%USERPROFILE%\Downloads\platform-tools\adb.exe" logcat -d -v time -s MLOGBridge:I > "%USERPROFILE%\Downloads\Bridge_v19_Log.txt"
```

Eventos `BRIDGE_IME_ENTER_SENT` e `BRIDGE_IME_ENTER_UNVERIFIED` indicam a tentativa pela nova via. `DIRECT_ENTER_SENT` indica retorno a v1.8. **Enviar Enter nao garante que o Flutter o processou**: validar leitura no MLOG.

## Seguranca e validacao

- APK nao foi compilado/testado neste ambiente. Java/XML passam por verificacao de sintaxe e formato; GitHub Actions compila e o CMX valida.
- Nenhuma permissao de internet, clipboard de leitura ou log de conteudos digitados.
- Ativar teclado do Android exige confirmacao do operador. Nunca conceder silenciosamente.
- Testar com registros de demonstracao e manter a v1.8 funcional como plano B.
