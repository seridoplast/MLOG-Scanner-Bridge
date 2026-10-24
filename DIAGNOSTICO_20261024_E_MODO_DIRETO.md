# MLOG Scanner Bridge 3.1.4 — Modo de leitura direta (experimental)

## Evidencias do diagnostico de 24/10/2026
- `eventos_android.txt`: o PdaBaseService registra uma decodificacao e envia **Saida de Foco** (`focusSpot output, cover true`) e **broadcast** para a mesma leitura.
- O Bridge registra `SET_TEXT_OK` para o mesmo codigo, criando possibilidade de **duas fontes digitando** no Flutter.
- A gravacao mostra o codigo duplicado/concatenado no campo e o aviso do MLOG de codigo inexistente.
- O `estado_teclado.txt` confirma `mCurMethodId=br.com.seridoplast.mlogbridge/.BridgeScanInputMethodService` e InputConnection Flutter ativa no momento do dump.
- A captura foi realizada com a versao **v3.0.6** (consta no log); nao serve como teste da v3.1.3.

## Como ativar em UM coletor
1. Ative acessibilidade do Bridge e selecione seu teclado compacto, se desejar testar a rota de Enter por IME.
2. No **Assistente de Digitalizacao** / Configuracoes de Saida:
   - **Saida de Foco: OFF**
   - **Ativar Cobertura de Foco: OFF**
   - **Saida por Transmissao: ON**
   - **Acao de Transmissao:** `br.com.seridoplast.mlogbridge.SCAN`
   - **Rotulo do codigo** (extra): `CmxCode`
   - **Delimitador/sufixo Enter:** OFF.
3. No CMXScanner, desligue saida por foco e sufixo Enter, ou pare de usa-lo como emissor; nao desative o servico nativo de leitura se ele fornece os broadcasts.
4. No Bridge > Ajustes > Leitor direto (experimental), ative **Somente transmissao do scanner**. Isso ativa o Enter do Bridge.
5. Teste primeiro em separacao/conferencia sem movimentacao real. Confirme em tela **cada unidade**.

**IMPORTANTE:** O Bridge NAO substitui o driver/servico nativo do leitor CMX/Hikvision. Ele substitui a camada de *saida* (digitacao/foco/Enter) a partir do broadcast do servico, quando configuravel. Um APK comum nao possui acesso garantido ao dispositivo de camera industrial nem permissao para modificar as configuracoes do fabricante.

## Protecoes e limitacoes
- Com modo direto ativo, broadcasts `com.cmx.scanner.DATA`, `com.hikvision.ACTION_INPUT` e `com.service.scanner.data` sao ignorados. So o canal privado acima e processado.
- Bipe recebido enquanto o campo anterior estiver ocupado e recusado com aviso; **nao se deve assumir que foi conferido**.
- Enter do Bridge: primeiro envia KeyEvent pelo IME ativo, depois tenta pela InputConnection da acessibilidade e por ultimo ACTION_IME_ENTER; no maximo um metodo que reporte sucesso por ciclo.
- Confirmacao enviada pelo Android nao prova gravacao no MLOG. **Nao ha fila que reenvie automaticamente bipes**.
- Se o codigo aparecer em dobro, o Bridge cancela a operacao em vez de dar Enter.
- Volte ao modo anterior desmarcando `Somente transmissao do scanner` e restaurando configuracao do assistente/CMX.
- O ID `br.com.seridoplast.mlogbridge.SCAN` funciona somente se o Assistente permitir editar a acao de transmissao; caso contrario sera necessario diagnosticar uma alternativa de integracao no aparelho.

## Publicacao
O projeto conserva o workflow GitHub Actions e a assinatura empresarial existente. Compilacao/execucao no Android nao foi validada neste ambiente; testar primeiro o CI e um coletor.
