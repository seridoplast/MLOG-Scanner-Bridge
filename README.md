# MLOG Scanner Bridge v1.2 — Campo e confirmação confiáveis

Projeto Android para Android 14 / CMX. Compatível com a v1.1 para atualização mantendo o pacote `br.com.seridoplast.mlogbridge` e, quando assinada com a mesma chave, as preferências.

## Correções desta versão

- Reconhecimento do botão de teclado do MLOG aprimorado: em Separação (inclusive com Quantidade), prioriza o botão exposto pela acessibilidade e só recorre às coordenadas dos layouts conhecidos quando necessário. Aguarda um único editor válido.
- Teclado Gboard permanece disponível, sem alternar o método de entrada.
- Quando o CMX envia Enter antes de o campo Flutter estar preparado, o Bridge aguarda **420–450 ms** e verifica se o código permanece no campo. Se permanecer, tenta `ACTION_IME_ENTER` e, na falta de efeito, a tecla **✓ / check / concluir** do Gboard.
- Tecla numérica sem legenda somente quando a janela é do **Gboard**, pelo menos 8 teclas numéricas estão identificadas e há exatamente um botão seguro no canto inferior direito. Não tenta acertar Enter clicando em coordenadas da tela do MLOG.
- Consulta e conferência de venda: sempre que receber nova leitura e o campo estiver sem foco, tentar selecionar o campo novamente antes de preencher. A consulta não abre teclado sozinha enquanto o usuário vê um resultado.
- Tela de atividades só abre o botão de teclado quando chega a leitura. Separação conserva os dois layouts (com e sem quantidade) da v1.1.
- Removido o ajuste visível de Enter do Bridge. O CMX permanece como fonte primária, e a recuperação do Bridge é automática e condicional.
- Sem comandos de configuração do scanner, sem permissão de internet e sem histórico de códigos.

### Limitações e cuidados

O resultado de `ACTION_IME_ENTER` não comprova que o Flutter processou a ação. Por isso há uma única verificação de seguimento, sem toques cegos no teclado. **Ainda é um protótipo e não foi executado no CMX nesta etapa.** Testar primeiro fora de pedidos reais; o Enter automatizado pode confirmar operações.

Se a tela não expuser o campo à acessibilidade ou o Gboard não expuser um botão identificável, o Bridge registrará o erro no Diagnóstico, sem improvisar ações arriscadas. Nas telas de produtos, o reconhecimento do botão nativo continua sujeito ao layout real do MLOG.

## Atualizar o repositório pelo CMD

1. Baixar `MLOG_Scanner_Bridge_v12_Enter_Foco_Continuo.zip` para Downloads.
2. Abrir CMD e executar:

```bat
cd /d "%USERPROFILE%\Downloads\MLOG-Scanner-Bridge"
powershell -NoProfile -Command "Expand-Archive -LiteralPath (Join-Path $env:USERPROFILE 'Downloads\MLOG_Scanner_Bridge_v12_Enter_Foco_Continuo.zip') -DestinationPath . -Force"
git add -A
git commit -m "MLOG Bridge v12 Enter e foco continuo"
git push origin main
```

Na execução bem-sucedida do GitHub Actions, baixe o artefato `MLOG-Scanner-Bridge-v12-APK`. Extraia `app-debug.apk` para Downloads.

## Instalar com depuração USB

```bat
"%USERPROFILE%\Downloads\platform-tools\adb.exe" devices
"%USERPROFILE%\Downloads\platform-tools\adb.exe" install -r "%USERPROFILE%\Downloads\app-debug.apk"
```

O dispositivo deve constar como `device` e a instalação terminar em `Success`. Se receber `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, não desinstale automaticamente: veja se a assinatura de debug foi alterada, pois a desinstalação pode apagar configurações e exigir autorizações novamente.

## Validação rápida no aparelho

1. Na tela **Bipar Atividades**, verificar que não abre antes da leitura e que confirma ✓ já no primeiro bipe.
2. Repetir **três** leituras seguidas (inclusive após rejeição pelo MLOG); nenhuma deve clicar na tecla `0`.
3. Em **Consulta**, consultar dois produtos diferentes seguidos sem tocar na barra entre eles.
4. Na **Busca da conferência de venda**, testar confirmação e nova leitura.
5. Na **Separação**, testar produto normal e produto com botão `Quantidade`, com e sem o campo já aberto.
6. Se falhar, abrir **Diagnóstico** e copiar o último status; informar a tela e se o código apareceu no campo ou no teclado.

## Segurança

Acessibilidade autorizada pelo Android, somente na janela do MLOG. Este APK de depuração não é uma versão auditada para 30 coletores; antes de distribuição empresarial usar assinatura estável e testes controlados.
