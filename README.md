# MLOG Scanner Bridge v3.0.3 — Enter lento e permissões com ✓ verde

Base **v3.0.2**, preservando leitura física normal com CMX, pré-foco, proteção temporal de **150 ms**, tratador de caracteres invisíveis, aviso de formatos de código e painel flutuante.

## Bipes automáticos: 1 físico + N−1 virtuais

- Repetição virtual envia o **Enter por teclado (KEYCODE_ENTER DOWN e UP)**, no mesmo estilo da implementação antiga v1.9, em vez de preferir `performEditorAction()`.
- Se ambas as teclas forem recusadas pela conexão, tenta uma única ação de editor como último recurso. **Nunca envia dois Enters para a mesma repetição.**
- Cadência experimental mais conservadora: **mínimo de 900 ms entre Enters virtuais**, em vez de 155 ms. Após inserir código via acessibilidade, espera **180 ms** antes do Enter (antes eram 50 ms).
- Quando a contagem `SEPARADO` aumenta, usa o incremento como evidência visual. Se não aumentar após 900 ms, **continua o lote na cadência lenta**, registra `AUTO_COUNT_UNVERIFIED_CONTINUING_N` e informa quantidade de Enters enviados vs incrementos observados. Isso não é confirmação de gravação.
- Proteção permanece para erro de campo, mudança de tela/produto, saltos anormais da contagem e cancelamento. Quantidade máxima 100. Em pedidos reais, testar com 3 antes de 10 ou 100, pois enviar Enter **não garante registro no MLOG**.

## Configuração inicial

O cartão de configuração inicial mostra indicadores **✓ verdes individuais** para:

1. Acessibilidade ativa;
2. Teclado Bridge habilitado;
3. Teclado Bridge selecionado como IME;
4. Bolha flutuante autorizada e ativa.

Cada indicador é atualizado ao retornar de Configurações ou na verificação periódica do aplicativo. O cartão vermelho some automaticamente quando os quatro requisitos forem satisfeitos. As permissões continuam dependentes da aprovação do operador pelo Android.

## Instalação

Extraia o ZIP na pasta local `MLOG-Scanner-Bridge`, faça `git add -A`, `git commit`, `git push origin main`, baixe o artefato **MLOG-Scanner-Bridge-v303-APK** do GitHub Actions e instale `app-debug.apk` via ADB.

> A compilação completa do APK e a interação real com o Flutter não foram executadas neste ambiente. Testes locais de Java verificam políticas e temporização, mas são insuficientes para garantir o total de itens gravados no pedido.
