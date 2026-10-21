# MLOG Scanner Bridge v0.7 — atividades + interface monocromática

Projeto Android (Java, Android 8+; testado **somente por inspeção estática** neste ambiente). Compilar no GitHub Actions incluído em `.github/workflows/gerar-apk.yml`. APK e integração precisam ser validados no coletor CMX Android 14.

## O que foi observado no vídeo de 11 segundos

- Tela: **Bipar Atividades de Separação**, com câmera e teclado na base, e indicador **Nenhuma atividade registrada**.
- O operador toca no botão de teclado (segundo botão, ao lado da câmera), e um teclado **numérico** é exibido.
- Depois da tentativa de leitura, aparece um alerta **Atenção!**. Pelo vídeo não é possível atribuir a causa do alerta exclusivamente ao scanner ou ao código informado.
- Como a tela **Busca da conferência de venda** funcionou na v0.6, esse comportamento foi preservado.

## Correções v0.7

1. Na tela de atividades, a leitura por broadcast inicia um estado temporário, verifica o título do MLOG e tenta acionar o botão inferior do teclado. Não oculta o IME **antes** de abrir a entrada (a v0.6 fazia isso).
2. Espera até 3,5 segundos por um único campo editável acessível. Só então oculta temporariamente o Gboard e tenta preencher com `ACTION_SET_TEXT`. Se não encontrar o campo, cancela e mostra o motivo em Diagnóstico.
3. Reconhece telas com título **Bipar Atividades de Separação**, inclusive um conjunto de pistas (nenhuma atividade + iniciar) quando o Flutter não publica o título completo.
4. Ao abrir o teclado do Android, o MLOG pode deixar de ser a janela ativa. A nova busca de janelas permite localizar a janela MLOG **atrás do IME**, rejeitando situações em que outro aplicativo tenha assumido o primeiro plano.
5. Não envia leituras quando aparece um alerta reconhecido ou quando há campo de senha, quantidade/peso ou mais de um campo editável elegível.
6. O diagnóstico de erro persiste por pelo menos 30 segundos antes de voltar ao resumo rotineiro da tela.
7. Interface em preto, branco e cinzas: Início / Ajustes / Diagnóstico / Sobre; modo Sistema, Escuro ou Claro; alternância pelo botão ☾; ícone do usuário como launcher e no cabeçalho.
8. Teste na tela inicial mostra o último código e formato **somente na tela enquanto o app estiver aberto**; código não é salvo em preferências nem enviado pela Internet.
9. Continua usando o Gboard do Android como teclado padrão. O Bridge não instala IME próprio.

## Segurança e limites

- O serviço de acessibilidade é declarado para **somente** `com.targetsistemas.target_mlog2`. Requer autorização manual no Android; não busca neutralizar Play Protect.
- `ACCESSIBILITY_ACTION_SET_TEXT` só funciona se o Flutter expuser e aceitar o campo. O vídeo não prova que isso ocorrerá. Se necessário, precisamos do diagnóstico e de um teste com o coletor.
- O acionamento por posição é um recurso experimental, **ligado por padrão somente para atividades e busca**, restrito à geometria aproximada 720×1440 e ao reconhecimento da tela. Pode não funcionar em outra resolução, tamanho de fonte ou versão MLOG. Desative caso clique errado. Não realiza confirmação automática por padrão.
- Os broadcasts do scanner CMX não são autenticados; outro aplicativo local poderia imitar a ação. Utilize apenas coletores corporativos confiáveis e faça testes fora da produção.
- Não solicita INTERNET, não usa clipboard para códigos e não cria histórico de leituras.
- As verificações neste ambiente não são substituto para execução real, assinatura de produção e auditoria de segurança.

## Como atualizar no Windows — CMD

O ZIP contém o projeto completo na raiz. Baixe o ZIP para Downloads:

```bat
cd /d "%USERPROFILE%\Downloads\MLOG-Scanner-Bridge"
powershell -NoProfile -Command "Expand-Archive -LiteralPath (Join-Path $env:USERPROFILE 'Downloads\MLOG_Scanner_Bridge_v7_Monocromatico.zip') -DestinationPath . -Force"
git add -A
git commit -m "MLOG Scanner Bridge v7 atividades e UI monocromatica"
git push origin main
```

No GitHub, entre em https://github.com/seridoplast/MLOG-Scanner-Bridge/actions, espere **Success**, abra a execução e faça download do artefato **MLOG-Scanner-Bridge-v7-APK**. Extraia `app-debug.apk` para Downloads.

## Instalar no CMX por depuração USB (ADB)

Com o aparelho conectado, depuração USB ativada e autorizado pelo usuário:

```bat
"%USERPROFILE%\Downloads\platform-tools\adb.exe" devices
"%USERPROFILE%\Downloads\platform-tools\adb.exe" install -r "%USERPROFILE%\Downloads\app-debug.apk"
"%USERPROFILE%\Downloads\platform-tools\adb.exe" shell appops get br.com.seridoplast.mlogbridge
```

A primeira linha precisa mostrar `device`, não `unauthorized`; quando houver `unauthorized`, desbloqueie o coletor e confirme a impressão digital do computador.

Se surgir `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, o APK foi assinado com outra chave. **Não desinstale automaticamente**: faça backup das preferências e consulte a seção de assinatura abaixo. O `-r` preserva os dados **apenas** quando a assinatura é compatível.

## Testes (sem pedidos reais)

1. **Início**: configurar CMX, apertar o gatilho, verificar se o último código apareceu no teste.
2. **Ajustes**: habilitar **Leitura assistida**, **Ocultar teclado** e **Toque guiado**. Manter **Enter automático desligado**. Ativar serviço de acessibilidade no Android e manter o teclado padrão do sistema.
3. **MLOG / Bipar Atividades de Separação**: com o botão de teclado visível, bipar uma vez sem tocar. Verificar se ele abre a entrada e se o código chega ao campo correto. Se aparecer um alerta, não confirmá-lo e consultar **Diagnóstico**.
4. **MLOG / Busca da conferência de venda**: confirmar que continua funcionando.
5. **MLOG / Separação de produtos**: ainda não validado nesta versão; não ativar heurística experimental sem teste.
6. Se falhar, copiar **Diagnóstico**. Esse texto não deve incluir os códigos lidos.

## Distribuição em 30 coletores

A assinatura debug do GitHub pode variar entre execuções. Para implantar em vários equipamentos, use **keystore de produção estável**, armazenada como GitHub Secret, e distribuição corporativa/MDM com aprovações de acessibilidade conforme política do Android. Não é correto distribuir 30 instalações de APK debug provisório pelo Drive com o Play Protect desativado.

### Por que aparece o alerta "A 136451 está Cancelada"?

No vídeo ampliado, o alerta do MLOG diz **"A 136451 está Cancelada."**, sob o título **"Atenção!"**. Isso indica que uma leitura chegou ao fluxo de conferência do MLOG quando a entrada foi aberta manualmente, mas aquela atividade estava cancelada; **não demonstra que o código foi rejeitado pelo scanner**. Não trate esse alerta como falha do Bridge. A v0.7 cancela qualquer inserção caso detecte um alerta aberto e não sobrescreve um campo já preenchido pelo scanner nativo, para reduzir o risco de leituras duplicadas.
