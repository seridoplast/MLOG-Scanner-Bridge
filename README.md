# MLOG Scanner Bridge — Seridóplast

**Versão:** 3.6.6 · Pacote: `br.com.seridoplast.mlogbridge`

## Novidades da v3.6.6
- Bolha flutuante de 40 dp com **15% de opacidade em todo o conjunto** (fundo preto e ícone). A área de toque invisível continua com 48 dp, e o painel expandido conserva a legibilidade.
- Busca manual em **Sobre → Buscar versões** limitada às **4 Releases mais recentes** do Bridge; a API do GitHub solicita apenas quatro registros e o aplicativo também aplica limite defensivo. Inclui estáveis e pré-releases (testes protegidos por senha).
- GitHub Actions publica **v3.6.6 como Release estável** no primeiro push bem-sucedido, com `rollout=100`. Isso habilita atualização automática da frota elegível; teste/controle de distribuição deve ocorrer antes da publicação.
- Mantidos a busca automática aproximada de 10 minutos, tentativas de instalação a cada ~2 minutos, o Shizuku, painel responsivo ao tema e recuperação por Volume.

## Publicação da v3.6.6

1. Extraia o ZIP e substitua o código do clone local.
2. Execute:
   ```bash
   git add .
   git commit -m "v3.6.6 - Bolha 15 por cento e quatro releases"
   git push origin main
   ```
3. O GitHub Actions compila e assina o APK. Se a tag `v3.6.6` ainda não existir, a publica diretamente como **Release estável** com `rollout=100`.
4. **Aviso de distribuição:** `rollout=100` disponibiliza a atualização automática para todos os coletores elegíveis. Para um teste controlado, não publique essa configuração sem antes reduzir o rollout no workflow.
5. O ZIP contém o projeto; o APK de instalação é gerado no Actions com a chave de assinatura corporativa.

## Funcionalidades anteriores (v3.6.4)

- **Painel flutuante atualizado:** ferramentas em cartões e ações redesenhadas, com estado Shizuku mais legível. O painel continua sem roubar foco do MLOG, exceto quando o campo numérico autorizado exige teclado.
- **Janela de atualizações do Bridge:** seleção em cartões arredondados com versão, estado ESTÁVEL ou TESTE e aviso de versão já instalada. Mantém bloqueio com senha para pré-releases e botão de instalação por Shizuku ou Android após a validação.
- **Atualizações oficiais do Shizuku:** seção independente em **Sobre**, com consulta manual, verificação diária opcional, download do APK e instalação manual. Fonte: `https://github.com/RikkaApps/Shizuku/releases`. Somente Release estável; valida pacote `moe.shizuku.privileged.api`, versão mais recente e certificado igual ao do Shizuku já instalado. Se o digest SHA-256 da Release estiver disponível, ele também é validado.
- **Automatizar o Shizuku:** verificação + download diários e instalação enquanto o coletor está inativo podem ser habilitados separadamente. **Ambos os controles começam desligados.** A instalação automática exige confirmação expressa e Shizuku autorizado, e pode ser atrasada pelo WorkManager (tentativas aproximadamente a cada 15 minutos). A atualização do próprio aplicativo Shizuku pode encerrar o serviço privilegiado e exigir novo início via ADB.
- **O Bridge segue com busca de atualizações a cada ~10 min**, e tentativas de instalar APK corporativo pendente a cada ~2 min. Sem alteração da leitura/Volume, senha de pré-release ou assinaturas do Bridge.

## Cuidados específicos do Shizuku

- Atualizar o **aplicativo** Shizuku não garante que o **serviço** privilegiado continue ativo após a instalação. Se parar, pode ser preciso reativá-lo no Shizuku ou via ADB. Nunca atualizar durante a separação/conferência.
- Em coletor onde Shizuku não esteja instalado, o Bridge **não instala automaticamente** uma primeira cópia. Instale o app oficial primeiro.
- O processo verifica a identidade da assinatura do APK contra o Shizuku existente; se o app instalado tiver outra assinatura, a atualização é **bloqueada**.
- O botão **Instalador Android** solicita confirmação padrão do sistema. A opção **Via Shizuku (teste)** usa o instalador privilegiado e pode ter retorno de sessão interrompido ao atualizar o próprio Shizuku.
- O WorkManager não garante os prazos configurados quando o Android limita tarefas em segundo plano.

## Observações operacionais

- A recuperação rápida por **Volume** foi preservada (pausa de 10 ms entre os toques na micro-navegação em telas compatíveis).
- O último código e o histórico de 4 leituras aparecem somente enquanto a Activity estiver ativa; não são salvos em preferências.
- O código do Bipe Normal permanece por compatibilidade, mas a interface oferece apenas Bipe Shizuku.
- O teste real em coletor é indispensável para confirmar a aparência e a instalação automática.
- A assinatura release depende dos GitHub Actions Secrets já configurados; não inclua certificados ou senhas no repositório.
