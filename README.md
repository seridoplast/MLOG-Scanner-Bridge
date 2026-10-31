# MLOG Scanner Bridge — Seridóplast

**Versão:** 3.6.0 · Pacote: `br.com.seridoplast.mlogbridge`

## Funcionamento

- **Bipe XU / Shizuku:** modo principal. O Bipe Normal permanece no código legado, mas não está disponível na interface e não é selecionado pela migração.
- **Recuperar leitura:** preservada a micro-navegação pelo botão Volume do MLOG (720×1440), com pausa de 10 ms entre os toques, em telas compatíveis. Não há garantia de tempo total ou confirmação de que o MLOG contabilizou a leitura.
- **Interface:** Início, Ajustes, Diagnóstico e Sobre, com textos enxutos. Em Sobre, o botão **Buscar atualização** lista releases estáveis e pré-releases. A pré-release só pode ser baixada/instalada manualmente com senha; a release estável não exige senha.
- **Atualizações estáveis:** verifica o GitHub `/releases/latest` a cada 1 hora, valida SHA-256, certificado, pacote e versão crescente. Somente Releases não marcadas como pré-release entram no canal automático. A distribuição depende de `rollout=0/10/25/50/100` na descrição.
- **Instalação:** tentativas iniciais logo após o download e novas tentativas em aproximadamente **2 minutos** enquanto houver APK estável em cache, via trabalhos individuais do WorkManager; fallback periódico de 15 minutos. O Android pode atrasar por Doze, economia de bateria ou restrições de segundo plano; não é cronômetro preciso.
- **Segurança operacional:** só instala após ao menos 2 minutos sem leitura Shizuku e com tela desligada, ou fora do MLOG com foco comprovado. Precisa do Shizuku ativo e autorizado. Nunca desinstale o Bridge para atualizar.

## Como publicar v3.6.0

1. Extraia o ZIP e copie os arquivos para seu repositório local.
2. `git add . && git commit -m "v3.6.0 - Atualizacoes estaveis e testes manuais" && git push origin main`.
3. Nesta versão **excepcionalmente**, o workflow publica a tag `v3.6.0` como **Release estável** (`rollout=100`) para validar a instalação automática. Isso torna TODOS os coletores elegíveis; teste primeiro em ambiente controlado ou altere rapidamente para `rollout=0/10` se não quiser distribuição geral.
4. As próximas versões geradas por `git push` voltam a ser pré-releases (`rollout=0`) até aprovação.
5. Após publicar, verifique no GitHub `/releases/latest` que a tag é `v3.6.0` e `prerelease=false`. Um coletor com versão anterior, Shizuku autorizado e atualização automática ligada tentará instalar somente quando estiver ocioso.

**Pré-release manual:** em **Sobre → Buscar atualização**, selecione a versão marcada `TESTE`, digite a senha de autorização e escolha instalar via Shizuku ou Android. Senha nunca é gravada em texto puro no projeto; é validada por verificador PBKDF2 local. A autorização expira após 2 minutos. Essa barreira do app não protege contra instalação direta via ADB/arquivo.

> O projeto não contém a keystore. A build é assinada pelo GitHub Actions usando os Secrets existentes e valida o certificado corporativo.

## Testes

O workflow roda testes Java de política de leitura, foco, entrada, código de barras, recuperação por Volume e atualização. A compilação Android de produção ocorre no GitHub Actions. Antes de liberar para toda a operação, confirme a instalação da v3.5.9 e as telas de separação, conferência e entrada no coletor piloto.
