# MLOG Scanner Bridge v3.4.1 — correção de compilação

O GitHub Actions da v3.4.0 apresentou erro `variable context might not have been initialized` em `ShizukuScanClient.java:28`.

**Correção**: o listener `OnBinderDeadListener` agora é criado no construtor, depois de inicializar `this.context`. A operação do Bipe Shizuku não foi alterada.

**Versão**: `app/build.gradle` foi atualizado para 3.4.1 para criar release independente, sem sobrescrever eventual release anterior.

**Teste obrigatório**: confirme o sucesso de `:app:compileReleaseJavaWithJavac` e `:app:assembleRelease` no GitHub Actions antes de baixar e instalar.
