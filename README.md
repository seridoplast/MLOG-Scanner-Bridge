# MLOG Scanner Bridge v2.9.7 — 150 ms e formato do código

Continuação direta da v2.9.6: nenhum ajuste de foco, de Enter ou de comportamento do teclado foi alterado.

## Novidades

- `TimedClickGate.COOLDOWN_MS = 150L`: cooldown fixo de 150 ms, sem fila de cliques, sem bloqueio permanente, sem Enter automático pelo Bridge.
- Na tela **Leitura de códigos** (teste de bipagem), aparece `Formato lido: Code 39`, `EAN-13`, `Code 128`, `QR Code` etc. O formato vem do campo de **metadados do broadcast do scanner CMX/Hikvision**.
- Nomes recebidos como `Code39`, `CODE_39` ou `MVID_CODE_CODE39` são exibidos como `Code 39`.
- Se o CMX não enviar essa informação, mostra **Formato lido: Nao informado pelo CMX**. Não deduz pela quantidade de dígitos, pois um código numérico de 13 dígitos também pode ser Code 39.
- Mantidos os controles da barra compacta, o botão Apagar, a ocultação dentro do MLOG, as logos e os ajustes ativados por padrão da v2.9.6.

## Atenção ao cooldown

A média observada na v2.6 foi 576 ms. Com 150 ms, o desbloqueio pode ocorrer antes de o MLOG finalizar a leitura anterior, aumentando o risco de toques duplos ou operações perdidas. Testar em pedidos fictícios antes de usar em produção.

## Atualizar pelo CMD

Colocar `MLOG_Scanner_Bridge_v297_150ms_Formato_Leitura.zip` em Downloads:

```bat
cd /d "%USERPROFILE%\Downloads\MLOG-Scanner-Bridge"
powershell -NoProfile -Command "Expand-Archive -LiteralPath (Join-Path $env:USERPROFILE 'Downloads\MLOG_Scanner_Bridge_v297_150ms_Formato_Leitura.zip') -DestinationPath . -Force"
git add -A
git commit -m "Bridge v297 150ms formato de leitura"
git push origin main && del /q "%USERPROFILE%\Downloads\MLOG_Scanner_Bridge_v297_150ms_Formato_Leitura.zip"
```

Depois baixar o artefato `MLOG-Scanner-Bridge-v297-APK` em https://github.com/seridoplast/MLOG-Scanner-Bridge/actions. Instalar:

```bat
"%USERPROFILE%\Downloads\platform-tools\adb.exe" devices
"%USERPROFILE%\Downloads\platform-tools\adb.exe" install -r "%USERPROFILE%\Downloads\app-debug.apk"
```

## Validação

- Rodar os testes Java puros `TimedClickGateTest` e `BarcodeFormatLabelTest`.
- Android SDK não está disponível neste ambiente; o APK precisa ser compilado pelo GitHub Actions.
- Testar uma etiqueta Code 39 e uma EAN-13, conferir a identificação **fornecida pelo CMX** e validar rajadas sem duplicações.
