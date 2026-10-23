# MLOG Scanner Bridge v3.0.7 — assinatura fixa

O codigo da v3.0.6 foi preservado (Conferencia de Venda, apagar segurando, tratador de codigo, menu avancado, interface e os bipes automaticos ocultos). **A alteracao desta versao e exclusiva do processo de assinatura e distribuicao.**

## O que mudou

- A build do GitHub Actions passa de `assembleDebug` para `assembleRelease`.
- A mesma chave de assinatura corporativa, restaurada de **GitHub Actions Secrets**, assina todos os APKs.
- O workflow verifica o fingerprint do certificado antes de publicar.
- `versionCode` cresce automaticamente a cada execucao do workflow.
- O artefato contem `app-release.apk` — nao `app-debug.apk`.
- A chave privada e a senha NAO ficam no projeto/repositório.

## Comece aqui

Leia **`CONFIGURAR_ASSINATURA_FIXA.md`** antes de dar o primeiro push. O workflow falhara intencionalmente sem os dois GitHub Actions Secrets da assinatura.

## Primeiro teste

A migracao de um APK antigo com certificado diferente para a chave corporativa exige desinstalar o Bridge antigo **uma unica vez**, apos registrar os ajustes. Depois disso, `adb install -r app-release.apk` preserva dados nas atualizacoes normalmente aceitas pelo Android.

A chave deve ser guardada com backup seguro: caso seja perdida, a atualizacao por cima dos APKs ja distribuidos deixa de ser possivel.
