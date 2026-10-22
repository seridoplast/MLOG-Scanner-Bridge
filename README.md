# MLOG Scanner Bridge v3.0.0 — Ferramentas e bipes automáticos

Base: v2.9.9. Leitura normal sem alterações de protocolo: pré-foco, 150 ms de bloqueio, CMX responsável pelo Enter físico e teclado compacto permanecem.

## Painel flutuante

Bolha: engrenagem vetorial, 40 dp, 50% de opacidade. Painel mais limpo com margens de 20 dp e interruptores deslizantes: **verde = ligado**, **vermelho = desligado**. Tocar na palavra **Bipes automáticos** abre/recolhe uma gaveta com **Total de bipes**, **Armar próximo bipe** e **Cancelar bipes automáticos**. O painel fecha após armar para não bloquear o MLOG.

## Repetições virtuais (N−1)

- Se o total for **100**, o operador faz **1 bipe físico**, e o Bridge tenta **99 repetições virtuais** do mesmo código, uma a uma: localizar/abrir editor nativo, preencher, disparar Enter e observar a conclusão antes de continuar.
- O intervalo **mínimo** é de 155 ms; **não** é garantido que 100 registros caibam em 15,5 segundos, pois a confirmação do Flutter pode demorar mais.
- O modo só inicia quando explicitamente armado; desarmado, não interfere no CMX. Cancelar não reenvia ações antigas.
- v3.0.0: adicionou fallback de Enter pela conexão de acessibilidade do Android API 33+ quando o teclado Bridge não está conectado; também tenta a ação IME do editor quando disponível, sem duplicar Enters. O modo normal segue deixando o Enter exclusivamente com o CMX.
- Por segurança, **não prossegue** se não observar a conclusão da leitura anterior. Isso pode interromper o lote em telas Flutter sem feedback de acessibilidade. Contar o registro visual **não prova gravação** de pedido/estoque. Nunca usar para quantidade ou peso sem validação do MLOG.

## Identificação real da simbologia

A tela Leitura de códigos mostra o valor decodificado e tenta reconhecer **Code 39, Code 128, GS1-128, EAN-13, EAN-8, UPC, ITF, QR Code, Micro QR, Data Matrix, PDF417, Aztec, MaxiCode, GS1 DataBar** e outros **somente se o CMX enviar o tipo no broadcast**. Lê mais campos e usa metadados também do broadcast duplicado que contenha o tipo. IDs numéricos do SDK permanecem como IDs, sem inventar mapeamento. Se a leitura não tiver tipo, mostra os **nomes dos campos recebidos**, permitindo diagnóstico sem expor o valor do código nos logs.

O conteúdo do código **não determina sua simbologia**: o mesmo texto pode ser representado em Code 39 ou Code 128, e até em QR Code. Sem metadados do CMX, não há identificação inequívoca. O Bridge não modifica configurações do leitor.

## Compilação e instalação

```bat
cd /d "%USERPROFILE%\Downloads\MLOG-Scanner-Bridge"
powershell -NoProfile -Command "Expand-Archive -LiteralPath (Join-Path $env:USERPROFILE 'Downloads\MLOG_Scanner_Bridge_v300_Menu_Bipes_Formatos.zip') -DestinationPath . -Force"
git add -A
git commit -m "Bridge v300 menu flutuante e repeticao de bipes"
git push origin main && del /q "%USERPROFILE%\Downloads\MLOG_Scanner_Bridge_v300_Menu_Bipes_Formatos.zip"
```

Depois: https://github.com/seridoplast/MLOG-Scanner-Bridge/actions — artefato **MLOG-Scanner-Bridge-v300-APK**.

```bat
"%USERPROFILE%\Downloads\platform-tools\adb.exe" devices
"%USERPROFILE%\Downloads\platform-tools\adb.exe" install -r "%USERPROFILE%\Downloads\app-debug.apk"
```

## Limitação de validação

Testes Java independentes do Android executados localmente; o ambiente atual não contém Android SDK para compilar o APK. Compilação completa e comportamento no CMX/Flutter precisam ser testados no Actions/coletor. Comece com 3 bipes em ambiente de teste e confira os registros efetivamente salvos no MLOG.
