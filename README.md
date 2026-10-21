# MLOG Scanner Bridge v1.4 — sincronização e fila por bipe

**Projeto Android experimental.** Base v1.3, atualizado após a captura ADB de 21/10/2026 no Android 14 do coletor CMX. Não substitui o APK do MLOG e não envia qualquer comando de configuração para o CMX Scanner.

## O que o diagnóstico revelou

Na tela de separação com quantidade, a hierarquia do Android expôs um botão de teclado próprio do MLOG (retângulo `[274,820][402,916]`) e, após a abertura, um `android.widget.EditText` focado em `[32,916][688,1056]`. Os horários extraídos do diagnóstico indicaram intervalos da ordem de 410–425 ms entre a leitura do scanner e a exibição do teclado em duas tentativas. **A hipótese é que o Enter nativo do CMX chega antes de o campo Flutter estar pronto**. Os avisos sobre código não encontrado não foram considerados falha do Bridge: o usuário confirmou que havia testado códigos diferentes dos do item.

## Mudanças na v1.4

- **Fila FIFO com limite de 6 leituras aguardando**: um novo bipe não cancela nem sobrescreve o anterior enquanto o campo está abrindo. Se a fila encher, registra a falha, em vez de fingir que recebeu a leitura.
- **Detecção reativa do campo**: além das verificações breves, escuta eventos de conteúdo/foco para avançar assim que o Flutter disponibilizar um editor válido.
- **Fluxo sequencial com timeout**: código recebido → botão MLOG → campo válido/focado → inserção → tentativa de Enter → liberação da leitura seguinte.
- **Enter de recuperação**: dá tempo ao Enter original do CMX; se o mesmo código ainda estiver no editor, tenta `ACTION_IME_ENTER` e depois, se necessário, o check identificado do Gboard. Nunca toca em teclas por coordenada genérica nem confirma quantidade ou senha.
- **Log técnico temporizado**: `MLOGBridge` no logcat identifica as etapas em milissegundos, **sem registrar conteúdo dos códigos**. Também exibe o último status na tela Diagnóstico.
- Preserva modo claro/escuro, ícone e Gboard, e mantém os modos de Consulta, Conferência, Atividades e Separação.

**Limites:** o Android pode sinalizar que um comando de Enter foi aceito sem isso significar que o MLOG concluiu uma operação. A leitura é confirmada de fato apenas pelo resultado apresentado no MLOG. Se o Flutter não expuser o editor ou o Gboard não expuser a tecla de confirmação, o Bridge registra o erro sem clicar às cegas. Testar em pedidos não operacionais antes de usar em produção.

## Gerar o APK pelo GitHub

Baixar `MLOG_Scanner_Bridge_v14_Sincronizado.zip` para Downloads. No CMD:

```bat
cd /d "%USERPROFILE%\Downloads\MLOG-Scanner-Bridge"
powershell -NoProfile -Command "Expand-Archive -LiteralPath (Join-Path $env:USERPROFILE 'Downloads\MLOG_Scanner_Bridge_v14_Sincronizado.zip') -DestinationPath . -Force"
git add -A
git commit -m "MLOG Bridge v14 sincronizacao por leitura"
git push origin main
```

Na página [GitHub Actions](https://github.com/seridoplast/MLOG-Scanner-Bridge/actions), após **Success**, baixe o artefato `MLOG-Scanner-Bridge-v14-APK` e extraia `app-debug.apk` para Downloads.

## Instalar no coletor via ADB

Ative depuração USB e autorize o PC no Android. No CMD:

```bat
"%USERPROFILE%\Downloads\platform-tools\adb.exe" devices
"%USERPROFILE%\Downloads\platform-tools\adb.exe" install -r "%USERPROFILE%\Downloads\app-debug.apk"
```

A instalação deve exibir `Success`. Se houver erro `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, as assinaturas são diferentes: **não desinstale sem preservar configurações**.

## Validação prática

1. Em tela de separação **com quantidade**, bipar um código correto somente uma vez: verificar que abre o botão de teclado do MLOG, insere o código e conclui com um único Enter.
2. Repetir com 2–3 leituras próximas. Não deve perder o segundo bipe; fila limitada protege a ordem.
3. Testar Separação sem quantidade, Bipar Atividades e Consulta (dois produtos seguidos).
4. Se falhar, na hora da falha execute `CAPTURAR_LOG_BRIDGE.cmd` depois de desbloquear e conectar o CMX. Ele cria `Downloads\MLOG_Bridge_Diagnostico.txt`; envie-o após conferir informações sensíveis.

## Validação desta entrega

O algoritmo da fila foi compilado e testado isoladamente com `javac` e testes FIFO. Os arquivos XML, o ZIP e as estruturas Java foram verificados estaticamente. **O APK completo ainda deve ser compilado no GitHub Actions e testado no equipamento físico**. Não há garantia de processamento em tempo fixo: o Flutter e o Gboard controlam a resposta do campo.
