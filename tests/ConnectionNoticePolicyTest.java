package br.com.seridoplast.mlogbridge;

public final class ConnectionNoticePolicyTest {
    private static void check(boolean ok){if(!ok)throw new AssertionError();}
    public static void main(String[] args){
        long first=1_800_000_000_000L;
        long day=ConnectionNoticePolicy.INTERVAL_MS;
        check(day==86_400_000L);
        check(ConnectionNoticePolicy.mayNotify(0L,first));
        check(!ConnectionNoticePolicy.mayNotify(first,first));
        check(!ConnectionNoticePolicy.mayNotify(first,first+5_000));
        check(!ConnectionNoticePolicy.mayNotify(first,first+day-1));
        check(ConnectionNoticePolicy.mayNotify(first,first+day));
        check(ConnectionNoticePolicy.mayNotify(first,first+2*day));
        check(!ConnectionNoticePolicy.mayNotify(first,first-60_000));
        check(!ConnectionNoticePolicy.mayNotify(0L,0L));
        // Repeated loss episodes after a successful reconnection do not unlock the daily window.
        AdbConnectionHealth h=new AdbConnectionHealth();
        h.healthy();h.lost(1000L);check(h.shouldAlert(16_000L));h.alerted();
        h.healthy();h.lost(20_000L);check(h.shouldAlert(35_000L));
        check(!ConnectionNoticePolicy.mayNotify(first,first+19_000L));
        // Reboot creates new in-memory health state, but the stored time still applies.
        h=new AdbConnectionHealth();h.healthy();h.lost(0L);check(h.shouldAlert(15_000L));
        check(!ConnectionNoticePolicy.mayNotify(first,first+60_000L));
        check(ConnectionNoticePolicy.mayNotify(first,first+day));
        System.out.println("ConnectionNoticePolicyTest OK");
    }
}
