# Versão 3.1.2 — tempo livre e interface simplificada

- Campo **Ajustes → Avançado → ms**: aceita números inteiros de **0 a 2.147.483.647 ms**, sem o limite anterior de 80 a 2.000 ms. O valor padrão continua 100 ms.
- `0 ms` retira o bloqueio temporal de leituras, mas mantém a proteção independente de rebote de gatilho (70 ms) e de broadcast duplicado. **Não significa bipes infinitos** nem remove os controles de segurança da leitura.
- Valores baixos podem gerar cliques antes de o MLOG concluir o processamento; faça teste com produtos fictícios antes de usar em produção.
- Textos explicativos reduzidos nas telas Início, Ajustes, Diagnóstico e Sobre; fonte das descrições secundárias reduzida de 13 para 12 sp e da explicação de título de 14 para 12 sp.
- Mantidas todas as funcionalidades anteriores, inclusive fallback de Enter na Conferência de Venda e o atualizador pela LAN.
- `versionName` atualizado para 3.1.2. O workflow mantém assinatura corporativa, incrementa o `versionCode` conforme execução e publica APK no GitHub Actions.
- **Não inclui APK compilado**; gerar no GitHub Actions e testar em um coletor antes de distribuir.
