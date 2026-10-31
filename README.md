# MLOG Scanner Bridge — Seridóplast

**Versão:** 3.5.9 · Pacote: `br.com.seridoplast.mlogbridge`

## Funcionamento

- **Bipe XU / Shizuku:** modo principal. O Bipe Normal permanece no código legado, mas não está disponível na interface e não é selecionado pela migração.
- **Recuperar leitura:** preservada a micro-navegação pelo botão Volume do MLOG (720×1440), com pausa de 10 ms entre os toques, em telas compatíveis. Não há garantia de tempo total ou confirmação de que o MLOG contabilizou a leitura.
- **Interface:** Início, Ajustes, Diagnóstico e Sobre, sem os textos técnicos extensos nem a opção de buscar DEBUG.
- **Atualizações estáveis:** verifica o GitHub `/releases/latest` a cada 2 horas, valida SHA-256, certificado, pacote e versão crescente. Somente Releases não marcadas como pré-release entram no canal automático. A distribuição depende de `rollout=0/10/25/50/100` na descrição.
- **Instalação:** tentativas iniciais logo após o download e novas tentativas em aproximadamente **2 minutos** enquanto houver APK estável em cache, via trabalhos individuais do WorkManager; fallback periódico de 15 minutos. O Android pode atrasar por Doze, economia de bateria ou restrições de segundo plano; não é cronômetro preciso.
- **Segurança operacional:** só instala após ao menos 2 minutos sem leitura Shizuku e com tela desligada, ou fora do MLOG com foco comprovado. Precisa do Shizuku ativo e autorizado. Nunca desinstale o Bridge para atualizar.

## Como gerar / aprovar v3.5.9

1. Extraia este ZIP e copie os arquivos para a pasta local do repositório.
2. `git add . && git commit -m "v3.5.9 - Interface enxuta, Bipe XU, instalador 2m" && git push origin main`.
3. O push compila, assina e publica **pré-release sem DEBUG** (`rollout=0`); teste o APK no coletor piloto via USB/ADB. Não há opção de pré-release dentro do Bridge.
4. Para distribuir a versão: GitHub → Actions → **Gerar APK do MLOG Scanner Bridge** → **Run workflow** → selecione `publish_release=true`, `rollout_percent=100` (ou 10 para piloto). O workflow promove a mesma tag e o mesmo APK para **Release estável**, visível à consulta automática.
5. Alternativamente, em Releases → Edit, desmarque Pre-release, atualize a descrição para `rollout=100`, marque Latest e salve. **Não substitua o APK** de uma versão já publicada.

> O projeto não contém keystore. A build Release usa os segredos corporativos existentes no GitHub Actions, verifica o certificado fixado e não pode ser recompilada localmente com assinatura equivalente sem a chave.

## Testes

O workflow roda testes Java de política de leitura, foco, entrada, código de barras, recuperação por Volume e atualização. A compilação Android de produção ocorre no GitHub Actions. Antes de liberar para toda a operação, confirme a instalação da v3.5.9 e as telas de separação, conferência e entrada no coletor piloto.

## Canal de atualizacoes (v3.5.10)

- A cada aproximadamente **2 horas**, o WorkManager consulta apenas `releases/latest` do GitHub. Pre-releases e drafts nao entram no canal automatico. O Android pode adiar as verificacoes.
- Em **Sobre > Buscar versoes**, o operador ve releases estaveis e pre-releases publicadas (ate 100 mais recentes). Pre-release exige senha para baixar/instalar pelo Bridge; senha nao fica em texto puro no codigo.
- O APK e validado por SHA-256, pacote, versao e certificado corporativo antes de instalar. Downgrade nao e permitido.
- A instalacao automatica respeita `rollout`, inatividade, Shizuku e a distincao estavel/DEBUG do APK em cache.
- A **v3.5.10** sera publicada como Release estavel com `rollout=100` apos o primeiro push e build bem-sucedido; **versoes seguintes voltam a pre-release ate aprovacao manual**.
- Se a tag `v3.5.10` ja existir no GitHub, o workflow nao substitui o APK: revise a Release existente ou incremente a versao.
