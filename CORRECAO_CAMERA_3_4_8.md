# MLOG Scanner Bridge v3.4.8 — Recuperacao pela camera do MLOG

## Evidencia
O diagnostico 31/10 e teste manual ADB confirmaram que abrir a camera interna
na tela de Separacao e retornar recupera a recepcao de leituras apos o teclado.
Nao se conhece a causa interna do Flutter, e o Bridge nao confirma que um bipe
foi contabilizado. Por isso, nao reenvia codigos automaticamente.

## Alteracoes
- Acessibilidade observa eventos de janelas de QUALQUER IME (inclusive Gboard).
- Quando um teclado permanece aberto >=250 ms e fecha, agenda a recuperacao
  depois de 450 ms, somente na tela de Separacao e com a camera identificada.
- O botao Resetar Bipe reaplica broadcast CMX e solicita a mesma recuperacao.
- Encontra o par de botoes da camera e do teclado pelas posicoes e tamanhos
  observados no CMX 720x1440, evitando toque cego em outras telas.
- Abre camera via gesto de acessibilidade e so envia Voltar quando detecta
  a tela da camera. Espera no minimo 360 ms, checando a cada 85 ms.
- Protecoes: cooldown 7 s, nenhuma recuperacao com IME visivel, lote armado,
  leitura recente ou tela/modal desconhecido; bloqueia leitura durante a
  transicao sem reenvio automatico. Falhas registradas como CAMERA_RECOVERY_*.
- A deteccao de teclado nao indica falha de contagem. A recuperacao e preventiva.

## Limites
- Se a camera nao for reconhecida pela acessibilidade, nao envia Voltar
  para evitar sair da atividade; operador pode precisar fechar manualmente.
- Se o MLOG mudar o layout dos botoes ou a camera, a recuperacao nao sera
  executada; verificar diagnostico e atualizar mapeamento.
- Testar primeiro em um CMX de homologacao e conferir quantidades exatas.
