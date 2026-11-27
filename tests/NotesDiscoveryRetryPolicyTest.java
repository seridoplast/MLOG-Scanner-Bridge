package br.com.seridoplast.mlogbridge;
public final class NotesDiscoveryRetryPolicyTest {
    static void check(boolean value,String text){if(!value)throw new AssertionError(text);}
    public static void main(String[] args){
        check(NotesDiscoveryRetryPolicy.ready(20000,0),"first attempt should be immediate");
        check(!NotesDiscoveryRetryPolicy.ready(34999,20000),"too early");
        check(NotesDiscoveryRetryPolicy.ready(35000,20000),"15-second retry");
        check(NotesDiscoveryRetryPolicy.ready(10,35000),"elapsed clock reset");
        check(!NotesDiscoveryRetryPolicy.ready(49000,35000),"should avoid network storm");
    }
}
