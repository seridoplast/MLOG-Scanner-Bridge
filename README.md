# MLOG Scanner Bridge — Seridóplast

**Versão:** 3.6.5 · Pacote: `br.com.seridoplast.mlogbridge`

## Novidades da v3.6.5
- Bolha flutuante visual reduzida de 52 dp para 40 dp, mantendo area de toque de 48 dp; fundo preto com opacidade de 50%.
- Painel flutuante acompanha o tema Claro, Escuro ou Sistema e atualiza sem reiniciar o servico.
- Versao mostrada no cabecalho obtida diretamente do APK instalado, sem texto fixo.
- Mantidas as atualizacoes do Shizuku, a seguranca de instalacao e o scanner da v3.6.4.

## Funcionalidades anteriores (v3.6.4)

- **Botão flutuante atualizado:** engrenagem vetorial azul com melhor contraste, painel escuro azul, ações redesenhadas e estado Shizuku mais legível. O painel continua sem roubar foco do MLOG, exceto quando o campo numérico autorizado exige teclado.
- **Janela de atualizações do Bridge:** seleção em cartões arredondados com versão, estado ESTÁVEL ou TESTE e aviso de versão já instalada. Mantém bloqueio com senha para pré-releases e botão de instalação por Shizuku ou Android após a validação.
- **Atualizações oficiais do Shizuku:** seção independente em **Sobre**, com consulta manual, verificação diária opcional, download do APK e instalação manual. Fonte: `https://github.com/RikkaApps/Shizuku/releases`. Somente Release estável; valida pacote `moe.shizuku.privileged.api`, versão mais recente e certificado igual ao do Shizuku já instalado. Se o digest SHA-256 da Release estiver disponível, ele também é validado.
- **Automatizar o Shizuku:** verificação + download diários e instalação enquanto o coletor está inativo podem ser habilitados separadamente. **Ambos os controles começam desligados.** A instalação automática exige confirmação expressa e Shizuku autorizado, e pode ser atrasada pelo WorkManager (tentativas aproximadamente a cada 15 minutos). A atualização do próprio aplicativo Shizuku pode encerrar o serviço privilegiado e exigir novo início via ADB.
- **O Bridge segue com busca de atualizações a cada ~10 min**, e tentativas de instalar APK corporativo pendente a cada ~2 min. Sem alteração da leitura/Volume, senha de pré-release ou assinaturas do Bridge.

## Publicação e testes

1. Extraia este ZIP. Copie o conteúdo da pasta `MLOG-Scanner-Bridge/` para o clone local.
2. Execute `git add .`, `git commit -m "v3.6.5 - Bolha discreta, tema e versao dinamica"` e `git push origin main`.
3. O fluxo gera uma **pré-release** `v3.6.5`, `rollout=0`, para testar via USB/ADB. Não habilite `rollout=100` nem publique Release estável antes de verificar em um coletor piloto.
4. Após o teste, no GitHub Releases, edite a mesma tag e desmarque *Pre-release*, marque *Latest* e configure `rollout=100` quando a frota estiver pronta. Como alternativa, execute o workflow manual `publish_release=true` e escolha o percentual desejado.
5. O APK assinado será gerado pelo GitHub Actions com os Secrets atuais. O ZIP contém o projeto, não o APK final.

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
