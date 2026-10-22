# Diagnóstico e diferenças da v3.0.0

- Na v2.9.9, os bipes automáticos podiam não iniciar porque a primeira confirmação precisava de uma mudança de texto no editor e o Enter virtual exigia IME Bridge ativo.
- Na v3.0.0, o primeiro bipe ainda exige conclusão observável no MLOG. Foram acrescentados Enter virtual por conexão do serviço de acessibilidade (Android 13+, testado em API de documentação) e ACTION_IME_ENTER disponível no próprio nó editável. **Não garante** que o Flutter exponha um campo compatível; interrupções continuam apresentando motivo no painel flutuante.
- O broadcast pode expor conteúdo decodificado sem o tipo real de código. Se não houver metadado de simbologia, não é possível deduzir de forma confiável Code 39, EAN-13, Code 128 ou QR Code apenas pelo valor textual.
- Na tela de teste, além do formato, são exibidos os nomes dos campos recebidos quando o tipo está ausente, sem registrar o conteúdo em log.
- Validar 3 bipes e comparar com registros salvos no MLOG; não avançar para 100 sem comprovar em todas as telas.
