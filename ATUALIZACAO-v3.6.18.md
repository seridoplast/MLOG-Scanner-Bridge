# MLOG Scanner Bridge v3.6.18 — operador MLOG

- Identifica o nome de login em campo não protegido na interface de autenticação do MLOG via acessibilidade, quando o Flutter expõe esse campo.
- Confirma a identificação apenas após encontrar uma tela operacional reconhecida; nunca lê ou transmite a senha.
- Armazena separadamente usuário da sessão e último usuário reconhecido. Ao retornar para a tela de login, limpa o usuário atual, mantendo o último para histórico.
- Transmite os campos `mlogOperator` e `lastMlogOperator` na telemetria ao painel Windows da v3.6.18.
- A Central exibe o usuário logado, o último login offline e preserva o nome fixo do coletor para permitir a gestão da frota.
- O reconhecimento depende da árvore de acessibilidade real do MLOG e deve ser validado no coletor piloto. Se o Flutter ocultar o campo ou não houver transição reconhecida, a Central mostra 'não identificado', sem inventar nomes.
- Pré-releases continuam manuais; esta versão está configurada para ser publicada como Release estável com rollout=100 após o push.

## Publicação

Copie o conteúdo do projeto ao repositório local (não substitua `releases.json` gerado automaticamente), depois execute:

```
git add .
git commit -m "v3.6.18 - Operador pelo login MLOG"
git pull --rebase origin main
git push origin main
```

Se houver conflito, resolva o rebase antes do push. Não use `--force`.
