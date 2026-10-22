# MLOG Scanner Bridge v2.9.8 — Ferramentas flutuantes

## Mantido da v2.9.7

- `TimedClickGate.COOLDOWN_MS = 150L` na leitura comum; nenhum novo Enter automático no modo comum.
- O CMX continua responsável pelo código e Enter da leitura física normal.
- Interface preta/branca, logos atuais, teclado compacto com Digitar/Apagar/trocar, formato do código no teste e as preferências anteriores.

## Botão flutuante SP (opcional)

1. Abra MLOG Scanner Bridge → **Início → Ativar / abrir botão flutuante**.
2. Autorize **Exibir sobre outros apps** quando solicitado; volte e pressione ativar novamente.
3. Arraste a bolha `SP` para outra posição; toque para expandir/recolher.
4. O painel apresenta **Tratar códigos invisíveis**, **Contador automático**, **Quantidade**, **Armar próximo bipe** e **Cancelar repetições**.
5. **Desativar botão flutuante** na tela inicial desliga as duas funções.

### Tratador de códigos

Habilitado manualmente, remove: CR, LF, Tab, outros controles, espaços Unicode, marcadores invisíveis e BOM. Preserva letras, dígitos, maiúsculas/minúsculas e pontuação do código (inclusive Code 39). Não transforma Code 39 em EAN-13, não inventa dígitos de verificação e não recupera uma simbologia que o scanner físico não decodificou.

**Limite técnico:** o tratamento opera no código recebido pelo **broadcast CMX/Hikvision** e na inserção que o Bridge controla. Caso o CMX envie o código e Enter diretamente como teclas antes de o broadcast chegar, o Bridge pode não conseguir substituir a entrada a tempo; alguns campos Flutter também recusam a modificação. Se o wedge preencheu o campo com código equivalente apenas após remover caracteres invisíveis, o Bridge tenta substituí-lo pelo tratado no campo de leitura reconhecido.

### Contador automático

- Deixe **Contador automático** marcado no painel e digite a quantidade total (de 1 a 100).
- Selecione **MLOG Bridge** como teclado padrão do Android; o contador automático depende do seu InputConnection para Enter nas repetições.
- Toque **Armar próximo bipe** (a janela recolhe) e leia o produto **UMA vez**.
- A primeira leitura e primeiro Enter continuam vindo do CMX. Ao verificar que o MLOG mudou ou fechou o campo, o Bridge tenta inserir o mesmo código e enviar Enter nas **N−1 repetições restantes**.
- Há **pelo menos 155 ms** entre tentativas; o tempo REAL pode ser muito maior, porque o Bridge espera o MLOG apresentar confirmação visual antes de cada próxima leitura.
- Se a tela mudar, aparecer um aviso, o campo não abrir, o Enter falhar ou não houver confirmação, o contador **cancela** em vez de executar repetições cegamente. O botão **Cancelar** invalida as tarefas pendentes imediatamente.
- Não aperte o gatilho durante as repetições: o Bridge ignora esses eventos, mas **não controla a emissão direta de teclas pelo driver CMX**.
- `3` significa **3 inserções no total** (primeiro bipe físico + 2 repetições virtuais). Não há bipes físicos adicionais do hardware.

**Limites / segurança operacional:** a mudança visual do Flutter não é prova de persistência no pedido ou no estoque. Confirme a quantidade registrada no MLOG e teste com produtos e pedidos fictícios antes de utilizar em separação, peso, conferência ou estoque real. 155 ms é o piso, não um tempo de conclusão garantido. O contador não deve ser usado em telas de quantidade/peso não homologadas.

## Atualizar pelo CMD

Coloque `MLOG_Scanner_Bridge_v298_Flutuante_Tratador_Contador.zip` em Downloads e execute no CMD:

```bat
cd /d "%USERPROFILE%\Downloads\MLOG-Scanner-Bridge"
powershell -NoProfile -Command "Expand-Archive -LiteralPath (Join-Path $env:USERPROFILE 'Downloads\MLOG_Scanner_Bridge_v298_Flutuante_Tratador_Contador.zip') -DestinationPath . -Force"
git add -A
git commit -m "Bridge v298 ferramentas flutuantes tratador contador"
git push origin main && del /q "%USERPROFILE%\Downloads\MLOG_Scanner_Bridge_v298_Flutuante_Tratador_Contador.zip"
```

**O ZIP somente é apagado se o `git push` terminar com sucesso.** Baixe o artefato `MLOG-Scanner-Bridge-v298-APK` em https://github.com/seridoplast/MLOG-Scanner-Bridge/actions.

### Instalação por ADB

```bat
"%USERPROFILE%\Downloads\platform-tools\adb.exe" devices
"%USERPROFILE%\Downloads\platform-tools\adb.exe" install -r "%USERPROFILE%\Downloads\app-debug.apk"
```

## Validação

- Todos os arquivos Java passaram pela análise sintática (`JavacTask.parse`).
- Testes locais puros passaram: `TimedClickGateTest`, `BarcodeFormatLabelTest`, `ScanDuplicatePolicyTest`, `ScanQueueTest`, `EnterReadinessPolicyTest`, `BarcodeCleanerTest`, `AutoRepeatPolicyTest`.
- Todos os arquivos XML passaram por parser XML.
- Não foi possível compilar o APK Android neste ambiente (Android SDK não instalado); o artefato GitHub Actions e o teste no **CMX Android 14/MLOG Flutter** ainda são necessários.
