# MLOG Scanner Bridge v0.6 — Busca da conferência e bipagem de atividades

Projeto Android Java para coletor CMX, baseado na v0.5 sem teclado próprio.
A compilação deve ser feita pelo GitHub Actions (`.github/workflows/gerar-apk.yml`).

## Melhorias desta versão

1. **Busca da conferência de venda**: reconhece o título "Busca da conferência de venda". Procura um único campo de entrada e tenta focalizá-lo sem mostrar o teclado. Se necessário, tenta focar o controle identificado pelo rótulo "Bipe ou digite o código da atividade". Há também uma tentativa por toque proporcional, restrita a essa tela.
2. **Bipar Atividades de Separação**: primeiro procura o botão de teclado identificado pela árvore de acessibilidade. Se o ícone Flutter não tiver descrição acessível, permite um toque proporcional apenas quando o título exato da atividade estiver presente e o coletor tiver a proporção de tela observada (720x1440).
3. **Sem troca de IME**: preserva o Gboard e tenta suprimir temporariamente o teclado virtual pela API oficial `SoftKeyboardController`.
4. **Separação de produtos e bobinas**: mantém o comportamento conservador da v0.5. A heurística de produtos continua desativada por padrão, pois ainda não foi testada.
5. **Segurança**: não solicita Internet, não persiste códigos lidos, não usa clipboard e não envia Enter por padrão. Só age em uma janela do MLOG (`com.targetsistemas.target_mlog2`), nas telas reconhecidas, e só preenche campos editáveis exclusivos. Não tenta inserir dados em Quantidade, Peso, Senha etc.

## Limitações reais

- O MLOG é Flutter; dependendo das informações que ele publica para acessibilidade, pode não existir um campo editável disponível via `ACTION_SET_TEXT`. Nessa situação o Bridge NÃO consegue inserir o código e registra um diagnóstico no app. Não é possível garantir a automação apenas a partir das capturas de tela.
- Os toques por posição relativa foram estimados a partir de capturas **720x1440** e dependem do layout real. Estão habilitados por padrão somente nas telas de conferência e de atividades, mediante título reconhecido, para facilitar o primeiro teste. **Desative essa opção se o coletor ou o layout for diferente.** Se houver qualquer clique incorreto, pare o teste e envie o diagnóstico.
- A permissão de acessibilidade continua exigindo autorização do Android. Use uma instalação corporativa autorizada; não desative o Play Protect.
- O app recebe broadcasts do scanner que não possuem autenticação de origem. Teste em coletor controlado, não em pedidos de produção, até confirmar a confiabilidade.
- O Enter está desligado. Mesmo quando um código é colocado no campo, talvez seja preciso confirmar manualmente enquanto não validarmos a semântica do Flutter.
- O APK é assinado com a chave de debug do GitHub Actions. Uma execução diferente pode gerar uma assinatura diferente; para 30 dispositivos, use uma chave de release estável e gestão de dispositivos.

## Testes recomendados (fora da operação)

1. Configure o scanner para enviar o código por broadcast e teste a leitura dentro do Bridge.
2. Mantenha o Gboard selecionado como teclado padrão.
3. Ative a acessibilidade autorizada e "Ativar as leituras assistidas"; deixe Enter automático desmarcado.
4. Na tela "Busca da conferência de venda", leia um código de teste e confira que entrou na caixa superior (não confirme a conferência de verdade).
5. Na tela "Bipar Atividades de Separação", leia um código de teste e confira que o botão do teclado abriu apenas o campo e que o código entrou nele.
6. Se falhar, volte ao Bridge e copie o texto de **Acessibilidade / diagnóstico** e envie junto com a imagem da tela.
7. Teste separação de produtos e bobinas depois; a v0.6 não afirma corrigir a parte ainda não validada.

## Enviar a atualização ao GitHub pelo CMD

O arquivo ZIP contém o projeto **completo**. Ele deve ser extraído diretamente sobre a pasta local do repositório Git, e NÃO como subpasta.

```bat
cd /d "%USERPROFILE%\Downloads\MLOG-Scanner-Bridge"
powershell -NoProfile -Command "Expand-Archive -LiteralPath (Join-Path $env:USERPROFILE 'Downloads\MLOG_Scanner_Bridge_v6_Conferencia_Atividades.zip') -DestinationPath . -Force"
git add -A
git commit -m "MLOG Bridge v6 busca conferencia e atividades"
git push origin main
```

GitHub Actions -> execução verde -> Artifacts -> `MLOG-Scanner-Bridge-v6-APK` -> `app-debug.apk`.

Se ocorrer `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, a assinatura de debug mudou. Preserve suas configurações se necessário e só então desinstale **o Bridge** anterior (não o MLOG) para instalar a versão nova. Para produção, use assinatura estável.

## Autorizações e distribuição

A instalação por ADB funcionou em um coletor Android 14 do usuário, mas não substitui a autorização de acessibilidade para outros equipamentos. Para 30 coletores, recomendamos MDM/Android Enterprise e versão release privada assinada, com testes e controle de atualização. Nunca utilize `appops` ou comandos de segurança para ocultar permissão sensível.
