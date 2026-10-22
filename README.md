# MLOG Scanner Bridge v2.9.4 — Bloqueio temporizado (450 ms)

## Base e diagnóstico

Esta edição parte da **v2.6** (fluxo que abria o campo do MLOG corretamente) e mantém as logos incorporadas nas versões posteriores. O arquivo `Diagnostico_MLOG_20261022_104146.zip` foi usado para a medição.

Entre 16 ciclos registrados, **12 terminaram com `SCAN_FINISH_campo_mudou` ou `SCAN_FINISH_campo_saiu`**. Os tempos `SCAN_ELAPSED_MS` destes 12 ciclos foram (em milissegundos):

`764, 507, 207, 707, 900, 148, 717, 380, 361, 1051, 997, 178`

- **Média: 576,4 ms**; mediana: **607 ms**.
- Bloqueio fixo definido: **450 ms**, cerca de 126 ms abaixo da média de 576 ms (e abaixo da mediana de 607 ms).
- Estes são tempos de processamento observados pelo Bridge, **não** uma prova do tempo do servidor nem da confirmação física do Enter. Há ciclos acima de 450 ms.

## Funcionamento

1. O primeiro gatilho aceito inicia a janela de 450 ms e tenta abrir/focar o campo do MLOG **uma única vez**.
2. Durante os 450 ms, novos gatilhos **não geram nenhum clique e não ficam em fila**. Leituras recebidas nesse intervalo são ignoradas pelo Bridge; o CMX continuará operando conforme sua própria configuração.
3. Decorridos 450 ms, o **próximo gatilho** inicia um ciclo novo, mesmo que o MLOG não tenha exposto a confirmação anterior ao Android. Não é necessário rearmar manualmente.
4. Se o código chegar por broadcast sem evento físico do gatilho, o próprio broadcast pode abrir um ciclo temporizado, respeitando o mesmo prazo. Retransmissões desse mesmo bipe são descartadas.
5. Um mesmo ciclo nunca envia um segundo clique atrasado no botão nativo do MLOG.
6. **O Bridge não envia Enter automático.** O Enter permanece sob responsabilidade do CMX Scanner, como na v2.6. A digitação manual do teclado Bridge permanece disponível.
7. A versão mantém o pré-foco pelo gatilho, o reconhecimento dos layouts de produtos (inclusive quantidade), consulta, atividades, o tema claro/escuro e as logos.

**Importante:** a proteção de 450 ms não espera a confirmação, então uma operação mais lenta que 450 ms pode ainda estar em processamento quando o próximo gatilho for aceito. Teste fora de pedidos reais e confira se não há duplicação/perda de itens.

## Atualização pelo CMD do Windows

Baixe `MLOG_Scanner_Bridge_v294_Bloqueio_450ms.zip` para Downloads e execute:

```bat
cd /d "%USERPROFILE%\Downloads\MLOG-Scanner-Bridge"
powershell -NoProfile -Command "Expand-Archive -LiteralPath (Join-Path $env:USERPROFILE 'Downloads\MLOG_Scanner_Bridge_v294_Bloqueio_450ms.zip') -DestinationPath . -Force"
git add -A
git commit -m "Bridge v294 bloqueio temporal de 450 ms"
git push origin main
```

Em https://github.com/seridoplast/MLOG-Scanner-Bridge/actions, abra a execução bem-sucedida e baixe o artefato `MLOG-Scanner-Bridge-v294-APK`. Extraia `app-debug.apk` para Downloads.

## Instalação pelo ADB

Conecte o coletor por USB, autorize a depuração e rode:

```bat
"%USERPROFILE%\Downloads\platform-tools\adb.exe" devices
"%USERPROFILE%\Downloads\platform-tools\adb.exe" install -r "%USERPROFILE%\Downloads\app-debug.apk"
```

Precisa aparecer `device` no primeiro comando e `Success` no segundo. A opção de Enter automático deve estar habilitada no CMX. O próprio Bridge não deve tentar enviar outro Enter.

## Diagnóstico da versão

```bat
"%USERPROFILE%\Downloads\platform-tools\adb.exe" logcat -d -v time -s MLOGBridge:I > "%USERPROFILE%\Downloads\Bridge_v294_Log.txt"
```

Eventos relevantes:
- `CLICK_COOLDOWN_STARTED_450_MS`: novo ciclo aceito.
- `CLICK_COOLDOWN_IGNORED_REMAINING_MS_...`: gatilho ignorado durante a janela.
- `SECOND_NATIVE_CLICK_PREVENTED_...`: clique repetido não executado.
- `BROADCAST_IGNORED_COOLDOWN_OR_ECHO_...`: transmissão repetida/bloqueada.
- `BROADCAST_REARM_NEW_CYCLE`: novo ciclo admitido sem evento físico de gatilho.
- `OLD_CYCLE_CANCELLED_ON_REARM`: tarefas antigas canceladas ao começar ciclo novo.

## Validação e limitações

- Código-fonte Java analisado sintaticamente com parser Java 17.
- Testes locais de janela de tempo, deduplicação, fila e políticas de Enter executados.
- Não há Android SDK e Gradle neste ambiente: **a compilação APK não foi executada aqui**.
- A versão precisa ser compilada no GitHub e validada em **um coletor de testes** antes de instalar em escala.

### Atenção ao intervalo reduzido

O novo prazo de 450 ms fica 126,4 ms abaixo da média observada (576,4 ms) e abaixo da mediana (607 ms). Algumas leituras no diagnóstico chegaram a superar 1 segundo; portanto, o desbloqueio pelo relógio não garante que o MLOG já tenha concluído o processamento. Não utilize a versão em pedidos reais antes de confirmar ausência de perdas e duplicações.
