# Diferenças entre v2.9.8 e v2.9.9

- Sem mudança no cooldown normal do Bridge: **150 ms**.
- Botão SP 55 dp (janela) e texto → engrenagem vetorial circular 40 dp, opacidade 50%.
- Painel expandido: espaçamento lateral/superior/inferior de 9 dp → 16 dp; largura 314 dp ajustada à tela.
- Contador: nome mudado para **Bipes automáticos (N−1)**, esclarecendo que 100 bipes = 1 físico + 99 virtuais. Mecanismo continua baseado na confirmação observada do MLOG e intervalo mínimo de 155 ms. Sem garantir conclusão em 155 ms.
- Tela de teste do scanner agora inspeciona o broadcast original (espaço, Tab, CR, LF e Unicode invisível) e exibe advertência imediatamente abaixo do formato lido.
- Leitura operacional normal continua recebendo o valor legado limpo com `trim`; no tratador opt-in, a limpeza usa o original bruto. Assim o diagnóstico não muda a interpretação do código em separação normal.
- Nunca persiste histórico ou código decodificado em disco.

# Testes locais

- `AutoRepeatPolicyTest`: 13 verificações, incluindo 100→99.
- `BarcodeArtifactReportTest`: 9 verificações.
- Testes dos demais componentes puros: sucesso.
- `ScannerProtocol` compilado com stub Android confirmou raw preservado + leitura normal `trim`.
- Parse Java: 24 arquivos sem erros sintáticos.
- XML: 9 recursos válidos.

APK Android e comportamento no coletor não foram validados neste ambiente.
