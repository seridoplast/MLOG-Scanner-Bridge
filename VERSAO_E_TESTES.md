# MLOG Scanner Bridge v3.0.1 — Bipes automáticos contínuos

Base: v3.0.0. Mantidos: botão flutuante, gaveta de ajustes, interruptores verde/vermelho, tratador de código, identificação de formatos, teclado compacto, 150 ms no clique normal, Enter do CMX no primeiro bipe físico.

## Ajuste solicitado

Na v3.0.0 o contador aguardava por até 2500 ms que o Flutter limpasse/alterasse o campo após **cada Enter virtual**. Se a atualização visual não aparecesse, encerrava no item 2/N.

Nesta versão, depois de cada inserção do código, o Bridge confirma que o campo contém o código, envia UM Enter por uma das conexões já existentes e **avança para a próxima repetição sem exigir atualização visual**. O intervalo mínimo entre Enters virtuais permanece 155 ms. Não há fila de gatilhos nem repetição de Enter em falhas.

Se o Flutter mantiver o código no campo após Enter, o Bridge tenta limpar o campo **antes de reinserir o mesmo código** para gerar uma alteração real e evitar que a repetição seguinte seja ignorada por texto idêntico.

**Critério de parada:** tela MLOG ausente, diálogo/bloqueio ou navegação, editor sem foco ou texto divergente, falha persistente de abertura/inserção de código, ausência de interface disponível para enviar Enter, cancelamento manual ou o total de bipes enviado.

**Aviso de integridade:** a mensagem 'N/N enviados' significa que o Bridge enviou N tentativas (1 física e N-1 virtuais), **não que o MLOG gravou N itens**. Mesmo uma chamada bem-sucedida ao editor/Enter não garante gravação pelo Flutter ou servidor. Use primeiro uma sequência de 3, depois 10 em um pedido de teste e confira a quantidade efetiva. Não use esse modo sem conferência em operações reais.

## Atualização pelo CMD (remove ZIP somente após push com sucesso)

```bat
cd /d "%USERPROFILE%\Downloads\MLOG-Scanner-Bridge"
powershell -NoProfile -Command "Expand-Archive -LiteralPath (Join-Path $env:USERPROFILE 'Downloads\MLOG_Scanner_Bridge_v301_Bipes_Continuos.zip') -DestinationPath . -Force"
git add -A
git commit -m "Bridge v301 bipes automaticos continuos sem bloqueio visual"
git push origin main && del /q "%USERPROFILE%\Downloads\MLOG_Scanner_Bridge_v301_Bipes_Continuos.zip"
```

GitHub Actions: artefato **MLOG-Scanner-Bridge-v301-APK**.

## ADB

```bat
"%USERPROFILE%\Downloads\platform-tools\adb.exe" devices
"%USERPROFILE%\Downloads\platform-tools\adb.exe" install -r "%USERPROFILE%\Downloads\app-debug.apk"
```

## Testes e limitações

Os testes locais validam a política de 155 ms, o cálculo de N−1 e a lógica de progressão sem confirmação visual. Não há um Android SDK completo nem coletor CMX conectado neste ambiente para compilação e ensaio de integração com Flutter.
