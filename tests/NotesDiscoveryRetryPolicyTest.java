package br.com.seridoplast.mlogbridge;
public final class NotesDiscoveryRetryPolicyTest {
    static void check(boolean value,String text){if(!value)throw new AssertionError(text);}
    public static void main(String[] args){
        check(NotesDiscoveryRetryPolicy.ready(20000,0),"first attempt should be immediate");
        check(!NotesDiscoveryRetryPolicy.ready(319999,20000),"too early");
        check(NotesDiscoveryRetryPolicy.ready(320000,20000),"five-minute retry");
        check(NotesDiscoveryRetryPolicy.ready(10,320000),"elapsed clock reset");
        check(!NotesDiscoveryRetryPolicy.ready(60000,35000),"should avoid network storm");
    }
}
