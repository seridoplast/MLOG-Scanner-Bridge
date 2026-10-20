# MLOG Scanner Bridge v0.2 — sem acessibilidade

Projeto Android Java para CMX Supply + MLOG 9.3.5. **Nao e um APK compilado.**

## Mudanca principal

A v0.1 usava `AccessibilityService` e o Android impedia ativa-la. A v0.2 REMOVE completamente esse servico e implementa `InputMethodService` (um teclado especial SEM teclas). O operador habilita e seleciona esse teclado nas configuracoes Android. Quando um campo do **MLOG** tem foco, o IME recebe broadcasts do CMX e usa `InputConnection.commitText` para inserir o codigo. Fora do MLOG, nao injeta nada. O recurso de Enter inicia **desligado**.

**Limitacao essencial:** o IME nao consegue focar campos de outro aplicativo. E preciso tocar no campo ao entrar na tela de leitura. Se o MLOG retirar o foco ao confirmar, sera necessario tocar de novo. Para foco totalmente automatico seria necessaria cooperacao do MLOG, acessibilidade autorizada ou uma integracao oficial da Target.

## Seguranca

- Sem permissao de internet, sem acessibilidade, sem gravacao de codigos em disco.
- O teclado e instalado/selecionado apenas por acao do usuario. Android mostra alerta normal ao ativar teclados de terceiros.
- A insercao so e permitida quando `EditorInfo.packageName == com.targetsistemas.target_mlog2` e o campo nao e senha.
- Nao use o teclado para textos gerais, senhas ou dados sensiveis; retorne ao teclado normal quando finalizar o teste.
- Broadcasts do CMX podem ser enviados por outros apps: esta versao e um prototipo de teste, nao um sistema validado para producao.
- A confiabilidade depende do firmware enviar os broadcasts; se nao entregar, o IME nao le.
- Formatos suportados dependem do hardware e da configuracao. Nao ha garantia de ler 'qualquer formato'.

## Compilacao e instalacao

- Substitua os arquivos do seu repositorio local pelo conteudo deste ZIP.
- Remova os arquivos legados `MlogAccessibilityService.java` e `accessibility_config.xml` do repositorio local.
- `git add -A && git commit -m "v2 sem acessibilidade" && git push origin main`.
- GitHub Actions -> execucao concluida -> Artifacts -> `MLOG-Scanner-Bridge-v2-APK` -> `app-debug.apk`.
- **Desinstale o app v0.1 antes de instalar a v0.2**: a assinatura de debug do GitHub Actions pode mudar a cada compilacao.
- Depois ative e selecione o IME pelo proprio aplicativo (sem mexer na acessibilidade).
- **Reative o Play Protect**, se havia desligado.

## Teste isolado

1. Instale e abra o APK; configure modo broadcast CMX; teste leitura na tela do Bridge.
2. Ative teclado especial em Android > Idiomas e entrada > Teclado na tela (ou usando o botao do Bridge).
3. Selecione `MLOG Scanner (sem teclas)`.
4. Abra o MLOG e toque uma vez no campo para codigo.
5. Acione o scanner e confira o dado, sem confirmar pedidos reais.
6. Ative Enter opcional somente se o preenchimento estiver correto.

O projeto nao foi compilado neste ambiente. A compilacao pelo GitHub e o teste real no CMX ainda sao necessarios.
