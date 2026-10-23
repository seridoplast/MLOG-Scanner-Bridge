# MLOG Scanner Bridge v3.0.6 — Conferência de Venda + Apagar contínuo

Base: versão **v3.0.5**. Preservados o tratador de códigos, a bolha, o menu **Ajustes → Avançado** com bloqueio regulável pelo operador (padrão 100 ms), o Enter da leitura física pelo CMX e os bipes automáticos **ocultos/desativados no código**.

## 1. Apagar ao segurar

O botão **⌫ Apagar** da barra compacta do teclado MLOG Bridge e o **⌫** do teclado expandido agora executam:

- toque simples: exclui imediatamente um caractere antes do cursor ou o texto selecionado;
- ao segurar: após **350 ms**, começa a apagar a cada **70 ms**;
- ao soltar, cancelar o toque, arrastar o dedo para fora ou fechar o teclado: exclui nada mais e remove os callbacks pendentes;
- fora do MLOG, o teclado continua abrindo normalmente.

Nenhum Enter extra é enviado por esta função.

## 2. Mapeamento de duas telas novas — Conferência de Venda

Baseado nas capturas **720×1440** fornecidas:

| Tela reconhecida | Marcador do MLOG | Botão de teclado |
| --- | --- | --- |
| Início da conferência | `Conferência de Venda` + `Para iniciar a conferência / bipe qualquer produto` | par câmera/teclado, **x≈68,1% / y≈46,9%** |
| Produto aberto | `Conferência de Venda` + `BIPAR PRODUTOS` | par câmera/teclado, **x≈68,1% / y≈56,4%** |

**Ordem de detecção**: botão de teclado exposto com dimensão/posição compatível → candidato clicável direito do par câmera/teclado → toque guiado somente se título, marcador da tela e proporção do aparelho forem compatíveis.

Os demais layouts, inclusive **Busca da Conferência de Venda**, continuam separados e intactos. Não usamos toque por coordenadas se o estado da tela for desconhecido, nem duplicamos cliques para a mesma leitura. A abertura inicia no gatilho e a inserção pelo broadcast mantém a lógica existente. O CMX permanece responsável pelo Enter.

**Atenção:** as posições são específicas do layout mostrado. Se uma atualização do MLOG mudar a disposição dos botões ou a semântica acessível, o Bridge não deverá tocar na tela desconhecida. Envie diagnóstico da tela para adaptar.

## 3. Atualização no CMD

Baixe `MLOG_Scanner_Bridge_v306_Conferencia_Apagar_Segurar.zip` para Downloads e execute:

```bat
cd /d "%USERPROFILE%\Downloads\MLOG-Scanner-Bridge"
powershell -NoProfile -Command "Expand-Archive -LiteralPath (Join-Path $env:USERPROFILE 'Downloads\MLOG_Scanner_Bridge_v306_Conferencia_Apagar_Segurar.zip') -DestinationPath . -Force"
git add -A
git commit -m "Bridge v306 conferencia venda e apagar segurando"
git push origin main && del /q "%USERPROFILE%\Downloads\MLOG_Scanner_Bridge_v306_Conferencia_Apagar_Segurar.zip"
```

A exclusão do ZIP só ocorre se `git push` sair com sucesso. GitHub Actions: artefato `MLOG-Scanner-Bridge-v306-APK`.

Instalação ADB:

```bat
"%USERPROFILE%\Downloads\platform-tools\adb.exe" devices
"%USERPROFILE%\Downloads\platform-tools\adb.exe" install -r "%USERPROFILE%\Downloads\app-debug.apk"
```

## 4. Validação

- Teste Java puro do mapeamento de ambas as telas; testes Java anteriores preservados.
- Verificação de sintaxe de fontes Java e análise XML.
- Arquivo ZIP verificado.
- **Compilação Android e validação no coletor CMX dependem do GitHub Actions e do teste físico.** Teste em pedido de homologação antes de usar em operação.
