# MLOG Scanner Bridge — Seridóplast

**Versão:** 3.6.3 · Pacote: `br.com.seridoplast.mlogbridge`

## Novidades da v3.6.3

- Cabeçalho mais limpo: removido o rótulo textual duplicado "SCANNER BRIDGE" (as imagens oficiais da marca permanecem).
- Indicadores, tema, modo de teste e controles flutuantes usam ícones vetoriais Android `VectorDrawable`, em lugar de emojis/símbolos decorativos.
- Nome do modo de operação corrigido para **Bipe Shizuku** em toda a interface.
- Nenhuma alteração na leitura, recuperação por Volume ou no mecanismo de atualização.


- Interface redesenhada com azul, cartões modernos, navegação por ícones e temas claro/escuro.
- Tela de teste com informação do leitor, destaque do último código e histórico rotativo das **4 últimas leituras** com tipo e horário.
- Nome do modo de leitura exibido como **Bipe Shizuku**, com funcionamento via Shizuku. Bipe Normal permanece no código, desativado e sem opção visível.
- Busca automática de **Releases estáveis** aproximadamente a cada **10 minutos**. O WorkManager utiliza tarefas individuais reencadeadas porque o Android não aceita períodos inferiores a 15 minutos em `PeriodicWorkRequest`.
- Download e instalação condicionados a rede, integridade, assinatura, disponibilidade do Shizuku, `rollout` e inatividade. APK pronto: novas tentativas a cada aproximadamente **2 minutos**, com fallback a cada 15 minutos.
- A busca manual em **Sobre → Buscar versões** mostra Releases e pré-releases. Pré-releases exigem a senha configurada; nunca entram no canal automático.

**Intervalos não são garantidos:** Doze, bateria, conectividade e escalonamento do Android podem adiar as tarefas. Os coletores não precisam manter o Bridge aberto.

## Publicar como Release estável

1. Extraia o ZIP e copie o conteúdo de `MLOG-Scanner-Bridge/` para seu clone local do repositório.
2. Execute: `git add .`, `git commit -m "v3.6.3 - Logo limpa e ícones vetoriais"` e `git push origin main`.
3. O GitHub Actions executa os testes, compila, verifica assinatura corporativa e, **somente na v3.6.3**, cria a Release **estável**, `latest`, com `rollout=100`, caso a tag `v3.6.3` ainda não exista.
4. **Atenção:** `rollout=100` autoriza a atualização automática em todos os coletores elegíveis. Para testar apenas um equipamento, altere a descrição da Release para `rollout=0` imediatamente e use a atualização manual; promova para `rollout=100` somente após validar.
5. A partir da próxima versão, cada `git push` volta a gerar pré-release com `rollout=0`. A aprovação pode ser feita pela interface GitHub Releases ou pelo `workflow_dispatch` com `publish_release=true`.

O APK está no GitHub Releases ou no artefato do GitHub Actions após a compilação. **O projeto ZIP não contém um APK assinado.**

## Observações operacionais

- A recuperação rápida por **Volume** foi preservada (pausa de 10 ms entre os toques na micro-navegação em telas compatíveis).
- O último código e o histórico de 4 leituras aparecem somente enquanto a Activity estiver ativa; não são salvos em preferências.
- O código do Bipe Normal permanece por compatibilidade, mas a interface oferece apenas Bipe Shizuku.
- O teste real em coletor é indispensável para confirmar a aparência e a instalação automática.
- A assinatura release depende dos GitHub Actions Secrets já configurados; não inclua certificados ou senhas no repositório.
