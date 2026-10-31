package br.com.seridoplast.mlogbridge;
/** Politica de agendamento. WorkManager pode adiar execucoes por energia/Doze. */
final class BridgeUpdateIntervals {
    static final int CHECK_MINUTES=10;
    static final int INSTALL_RETRY_MINUTES=2;
    private BridgeUpdateIntervals(){}
}
