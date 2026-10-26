# MLOG Scanner Bridge v3.2.3 — Releases, desempenho e gatilho de 1,5 s

## Atualização pela GitHub Release

- Versão centralizada em `app/build.gradle`: `def bridgeVersion = '3.2.3'`.
- `versionName`: `3.2.3`; `versionCode`: `1030203` (calculado automaticamente). Os códigos antigos eram `100000 + GITHUB_RUN_NUMBER`: o novo é maior e permite a atualização por cima, com a mesma assinatura.
- No `push` à `main` do repositório **público**, o GitHub Actions assina e publica uma Release com a tag `v3.2.3` e o arquivo `MLOG-Scanner-Bridge-v3.2.3.apk`.
- Se a Release da mesma versão já existir, **não substitui** o APK publicado. Incremente `bridgeVersion` antes do próximo lançamento.
- O atualizador interno valida `tag_name`, `versionCode`, `versionName`, hash SHA-256 do GitHub e certificado corporativo antes de propor instalar o APK. Aceita o arquivo `app-release.apk` para compatibilidade com Releases antigas **desde que a tag seja semântica**.
- A v3.2.2 só reconhece `bridge-build-N`, então **instale a v3.2.3 uma vez por ADB**, sem desinstalar. A partir daí use o botão Sobre > Buscar atualização.

## Otimização das leituras

- O modo de leitura nativo **não percorre a árvore de acessibilidade durante o recebimento de cada broadcast** para fazer duas buscas redundantes. O código entra na fila imediatamente. `onBarcode` verifica a tela; a checagem de modal/campo/código exato continua antes do Enter.
- Na verificação final, a leitura do cartão do produto reutiliza o `root` já consultado, sem outra busca de janela ativa.
- Enquanto o Enter nativo está agendado, os eventos `TYPE_WINDOW_CONTENT_CHANGED` não repetem varreduras completas para modais. `TYPE_WINDOW_STATE_CHANGED` e a checagem imediatamente anterior ao Enter continuam protegendo a navegação.
- Preservado o valor de intervalo de ms, a checagem de texto completo antes do Enter, a fila e os filtros de broadcast.
- **Não há garantia de 100 ms por produto:** o Android e o Flutter possuem latências próprias. Validar por diagnóstico em um coletor.

## Gatilho longo: agora 1,5 s

- Opção **desativada por padrão** nos Ajustes.
- Segurar por 1.500 ms inicia uma série de **pedidos de leitura** ao serviço Hikrobot, via broadcast `com.service.scanner.start.scanning`. Não falsifica `KeyEvent` do Android, pois um aplicativo comum não tem privilégios para simular gatilho físico real.
- Nova tentativa após no mínimo 250 ms, respeitando intervalo maior configurado pelo usuário. Se já houver 3 leituras pendentes ou editor aguardando, pausa novas tentativas.
- Soltar envia `com.service.scanner.stop.scanning` e cancela callbacks. Parada de segurança após 30 s, caso o Android perca o evento de soltar.
- A leitura contínua depende do suporte desse comando pelo firmware. O serviço pode ignorar comandos ou não gerar novos bipes em alguns coletores; **exige teste físico com pedido de demonstração**.

## Validação e instalação

O projeto contém testes Java de regras, mas o APK não foi compilado neste ambiente (SDK Android/Gradle indisponíveis). Compilar e assinar por GitHub Actions, baixar o APK do Artifact e usar:

```powershell
cd "$env:USERPROFILE\Downloads"
.\platform-tools\adb.exe devices
.\platform-tools\adb.exe install -r ".\MLOG-Scanner-Bridge-v3.2.3.apk"
```

Não desinstalar a versão anterior, para preservar dados e autorizações. Acessibilidade pode eventualmente exigir reativação após atualização, conforme o Android/firmware. Testar com quantidade conhecida antes de usar em estoque real.
