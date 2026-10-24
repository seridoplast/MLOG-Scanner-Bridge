# MLOG Scanner Bridge 3.1.5 — integração experimental com leitor nativo CMX/Hikrobot

## O que mudou

- Foi incluído o `NativeScannerController`, que envia broadcasts **diretamente** ao serviço de hardware `com.hikrobotics.pdaservice`, sem iniciar o app CMXScanner ou acessar a interface do Assistente de Digitalização.
- Em Ajustes há a opção **Controlar leitor pelo Bridge (teste)**. Ela é **desligada por padrão**.
- Ao ativar, o Bridge configura o serviço para saída **broadcast-only**, sem sufixo Enter nem clipboard, canal `br.com.seridoplast.mlogbridge.SCAN` e campo `ScanCode`.
- A leitura continua exigindo o **serviço nativo** do fabricante para acionar e decodificar o hardware. O APK comum não substitui o driver do leitor.
- Na tela mapeada de conferência/separação, o Bridge tenta primeiro `ACTION_IME_ENTER` no campo reconhecido/focado, sem precisar reaproveitar uma InputConnection antiga. Se isso falha, tenta o teclado próprio ou a conexão de acessibilidade, sem duas tentativas com resultado de sucesso no mesmo ciclo.
- No modo nativo, até 8 leituras podem ficar aguardando na fila enquanto a leitura anterior processa, **mas só avançam depois de uma mudança visível do campo do MLOG**. Em caso de confirmação não observável, a fila é interrompida e o operador deve conferir o pedido manualmente.
- O diagnóstico registra eventos sem incluir o conteúdo do código lido (`NATIVE_SCANNER_CONFIG_SENT_ACTION_DIRECT`, `NATIVE_SCANNER_DELIVERY_OK`, `NATIVE_ENTER_ACTION_IME_ENTER_SENT`, `NATIVE_ENTER_UNVERIFIED_AFTER_ATTEMPT`).

## Procedimento em UM coletor de teste

1. Use pedido de treinamento, não movimentação real de estoque.
2. Faça **foto** das configurações atuais do Assistente de Digitalização (principalmente o modo de saída e sufixo), antes de mudar qualquer coisa.
3. Atualize o Scanner Bridge com `adb install -r` e mantenha a sua acessibilidade ativada.
4. Em **Ajustes → Leitor do coletor (novo)**, selecione **Controlar leitor pelo Bridge (teste)** e confirme.
5. Na tela de Início do Bridge, bipa um código e confirme que o aplicativo recebeu a leitura. Depois entre em **Diagnóstico** e confira se há o log `NATIVE_SCANNER_DELIVERY_OK` (via `adb logcat`).
6. Abra o MLOG em uma tela reconhecida e teste: 1 bipe; depois 2 bipes separados; depois 5 bipes. Confira fisicamente a quantidade registrada após CADA leitura.
7. Caso a leitura não chegue ao Bridge, seu firmware pode bloquear os comandos ou usar outro protocolo. Nesse caso, use a opção de desligar o modo nativo e restaure a saída original no Assistente de Digitalização usando as fotos.

**Atenção:** a operação de ativar envia comandos de alteração GLOBAL ao scanner. Desligar a opção no Bridge *não restaura* automaticamente as configurações antigas de hardware. Não há captura garantida do estado anterior via API nesta versão.

## Limites e segurança

- Não é possível garantir Enter e confirmação em um aplicativo Flutter de terceiros apenas porque `performAction` retornou `true`. Um resultado entregue ao Android não garante lançamento de movimentação pelo MLOG.
- A configuração de hardware foi documentada em outros PDAs Hikrobot. Neste CMX Supply ela ainda não foi validada em campo.
- Se o serviço do fabricante não fornecer broadcasts utilizáveis ou exigir assinatura de sistema, um APK comum não terá acesso direto confiável ao imager. Nesse caso é necessário SDK/firmware fornecido pelo fabricante ou integração oficial do MLOG.
- Este ZIP contém **código-fonte**. O workflow GitHub Actions é responsável por compilar/assinar, e a instalação em coletor ainda precisa ser testada.

## Publicação e diagnóstico

Depois de fazer o push no repositório, baixe o `app-release.apk` assinado pelo workflow.
No Windows (na pasta Downloads):

```powershell
.\platform-tools\adb.exe install -r .\app-release.apk
.\platform-tools\adb.exe logcat -c
# Reproduza um bipe e depois capture:
.\platform-tools\adb.exe logcat -d -s MLOGBridge:I > .\diagnostico_bridge_native.txt
```

Envie somente o arquivo de diagnóstico **sem informações privadas**. O código intencionalmente não registra os valores dos códigos de barras.

Referência de protocolo estudado (integração reversa, não manual oficial do CMX): https://github.com/kndhjk/pda-scan-bridge
