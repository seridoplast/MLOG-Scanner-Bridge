# Versão 3.0.6 — alterações isoladas

1. `BridgeScanInputMethodService.java`: `eraseKey()` com toque único e repetição sob pressão (350 ms de espera / 70 ms entre exclusões), cancelando ao soltar e ao fechar o teclado. Funciona no botão da barra compacta e no botão do QWERTY/número.
2. `ConferenceScreenPolicy.java`: os dois layouts de Conferência de Venda são identificados separadamente (antes do primeiro produto / cartão de produto ativo). Coordenadas relativas somente para capturas 720×1440 com título e marcador comprovados.
3. `MlogFocusAccessibilityService.java`: classificação das telas; identificação semântica ou espacial conservadora do botão nativo à direita da câmera; pré-foco no gatilho e fallback por broadcast; processamento do código e Enter do CMX preservados.
4. `tests/ConferenceScreenPolicyTest.java`: prova de reconhecimento, bloqueio de telas incorretas, posições relativas e geometria do par de botões.
5. `app/build.gradle`: `versionCode 43`, `versionName 3.0.6-conferencia-apagar-continuo`.
6. `.github/workflows/gerar-apk.yml`: artefato `MLOG-Scanner-Bridge-v306-APK`.
7. Tempo ajustável pelo operador mantido (100 ms padrão), bipes automáticos desativados internamente.

A verificação de sintaxe não é equivalente à compilação com Android SDK ou teste funcional. Homologar os dois estados em coletor antes de usar em pedidos reais.
