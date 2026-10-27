# MLOG Scanner Bridge v3.4.0 — Bipe Shizuku (experimental)

## Objetivo
Reproduzir no aparelho, SEM abrir o teclado visual do MLOG, o par de comandos
ADB validado no coletor pelo operador:

```
adb shell input text 7896707342948
adb shell input keyevent 66
```

A confirmação de que o MLOG realmente registrou o item foi feita **somente com
ADB de um PC**. O modo Shizuku necessita teste real no coletor.

## Arquitetura

1. O serviço nativo Hikrobot/CMX é configurado para **broadcast exclusivo**,
   saída de teclado, sufixo/Enter e clipboard desativados.
2. O `MlogFocusAccessibilityService` fica ativo **apenas para receber o
   broadcast e conferir que a janela ativa pertence ao MLOG**. No novo modo,
   não digita, não pressiona Enter, não clica em botões, não abre teclado e não
   intercepta teclas físicas.
3. `ShizukuScanClient` conecta-se à API Shizuku e a um **UserService** com UID
   `shell` (2000). Solicita permissão do operador pelo Shizuku.
4. `ShizukuShellScanService` executa `input text <codigo>` e depois `input
   keyevent 66` no próprio CMX, sem PC, usando `ProcessBuilder` com argumentos
   separados (sem `sh -c`).
5. Fila serial limitada a 8 leituras pendentes, sem repetição automática e sem
   eliminar códigos iguais (quantidades consecutivas continuam possíveis).
   Falhas são registradas no estado do app, sem guardar os códigos em log.

## Como usar

1. Inicie o Shizuku pelo comando mostrado no app Shizuku usando ADB, como já
   feito. O Shizuku deve informar **Em execução**.
2. No Bridge, confirme **Acessibilidade do Bridge** ativa.
3. Abra **Ajustes > Bipe Shizuku > Ativar Bipe Shizuku**.
4. Se solicitado, autorize **MLOG Scanner Bridge** no diálogo do Shizuku.
   Pressione Ativar Bipe Shizuku mais uma vez.
5. Abra uma conferência de TREINAMENTO no MLOG **sem tocar no botão de teclado**;
   use apenas um código válido e confira o registro real da quantidade.
6. Se não houver leitura, abra Bridge > Diagnóstico e examine `last_status`;
   use `adb logcat -s MLOGBridge:I` para eventos técnicos sem código.
7. Para voltar imediatamente ao modo anterior, selecione **Bipe Zebra > Profile0**.

## Limites, garantias e segurança

- Modo isolado e desativado por padrão. O Profile0, IME e antigo permanecem
  como alternativas.
- **Reinicialização do coletor exige religar o Shizuku via ADB.** O modo
  Shizuku não emula o Zebra sem esse serviço auxiliar.
- Requer acessibilidade ligada como receptor; ela NÃO atua sobre os campos.
- Os códigos aceitos nesta primeira prova são apenas ASCII alfanuméricos,
  de 1 a 80 caracteres. Caracteres de controle, pontuação e espaços são
  recusados, sem corrigir nem truncar silenciosamente o original.
- Cada comando `input` cria processo; pode haver latência. A fila impede
  sobreposição dos pares código/Enter, mas não comprova processamento pelo MLOG.
- Código recebido fora do MLOG ou sem janela ativa identificada é descartado.
  Se uma leitura falhar, **não há replay automático** para evitar registro duplo.
- Os testes locais apenas validaram a política de códigos e sintaxe da mudança;
  o APK assinado e compilado será gerado pelo GitHub Actions após push.
