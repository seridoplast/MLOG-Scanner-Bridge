package br.com.seridoplast.mlogbridge;

/** Only show an active message category; never confuse order notes and general notices. */
final class FloatingMessageViewPolicy {
    static final int EMPTY=0;
    static final int OBSERVATION=1;
    static final int GENERAL_NOTICE=2;
    static final int COMPLETION=3;
    private FloatingMessageViewPolicy(){}
    static int select(int requested,int observations,int generalNotices,int pendingOrders){
        if(requested==OBSERVATION && observations>0)return OBSERVATION;
        if(requested==GENERAL_NOTICE && generalNotices>0)return GENERAL_NOTICE;
        if(requested==COMPLETION && pendingOrders>0)return COMPLETION;
        if(observations>0)return OBSERVATION;
        if(generalNotices>0)return GENERAL_NOTICE;
        if(pendingOrders>0)return COMPLETION;
        return EMPTY;
    }
}
