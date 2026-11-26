package br.com.seridoplast.mlogbridge;

import java.net.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class OfflineShellProtocolTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        byte[] secret=OfflineShellProtocol.nonce();
        check(java.util.Arrays.equals(secret,OfflineShellProtocol.key(OfflineShellProtocol.hex(secret))),"key roundtrip");
        check(OfflineShellProtocol.valid("SCAN:7891234567890"),"numeric barcode");
        check(OfflineShellProtocol.valid("SCAN:Ab-._/+123"),"valid legacy punctuation");
        check(!OfflineShellProtocol.valid("SCAN:123; input keyevent 4"),"reject shell syntax");
        check(!OfflineShellProtocol.valid("SCAN:123%s"),"reject special input escape");
        check(!OfflineShellProtocol.valid("SCAN:"),"reject empty code");
        check(!OfflineShellProtocol.valid("SCAN:"+"9".repeat(81)),"reject oversized code");
        check(!OfflineShellProtocol.valid("INSTALL:/tmp/a.apk"),"no package installation over local agent");
        check(!OfflineShellProtocol.valid("VOLUME:1:2:3"),"reject arbitrary tap");
        check(OfflineShellProtocol.valid("VOLUME:648:175:10"),"verified Volume sequence");
        AtomicInteger scans=new AtomicInteger();
        ExecutorService pool=Executors.newSingleThreadExecutor();
        try(ServerSocket server=new ServerSocket()){
            server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),0));
            server.setSoTimeout(5000);
            Future<?> serving=pool.submit(()->{
                for(int i=0;i<6;i++)try(Socket s=server.accept()){
                    OfflineShellProtocol.serve(s,secret,request->{
                        if(request.startsWith("SCAN:"))scans.incrementAndGet();
                        return !request.equals("SCAN:FAIL");
                    });
                }catch(Exception ignored){}
            });
            int port=server.getLocalPort();
            check(OfflineShellProtocol.call(port,secret,"PING",1500),"authenticated ping");
            check(OfflineShellProtocol.call(port,secret,"SCAN:123",1500),"first scan");
            check(OfflineShellProtocol.call(port,secret,"SCAN:123",1500),"intentional repeat");
            check(scans.get()==2,"no coalescing intentional repeat");
            try{OfflineShellProtocol.call(port,OfflineShellProtocol.nonce(),"SCAN:777",1500);throw new AssertionError("wrong key accepted");}catch(OfflineShellProtocol.BeforeDispatch expected){}
            check(scans.get()==2,"wrong key cannot execute");
            check(!OfflineShellProtocol.call(port,secret,"SCAN:FAIL",1500),"authenticated failed response");
            check(scans.get()==3,"failure not replayed");
            check(OfflineShellProtocol.call(port,secret,"STOP",1500),"authenticated shutdown");
            serving.get(6,TimeUnit.SECONDS);
        }finally{pool.shutdownNow();}
        // Disconnect after server executes, before acknowledgement: client must surface
        // uncertainty and must not reconnect/replay internally.
        try(ServerSocket server=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){
            ExecutorService one=Executors.newSingleThreadExecutor();AtomicInteger effects=new AtomicInteger();
            try{
                Future<?> f=one.submit(()->{try(Socket s=server.accept()){
                    OfflineShellProtocol.serve(s,secret,req->{effects.incrementAndGet();s.close();return true;});
                }catch(Exception ignored){}});
                try{OfflineShellProtocol.call(server.getLocalPort(),secret,"SCAN:123",1000);throw new AssertionError("expected unconfirmed operation");}catch(OfflineShellProtocol.BeforeDispatch unexpected){throw new AssertionError("post-dispatch failure must never allow fallback",unexpected);}catch(IOException expected){}
                f.get(2,TimeUnit.SECONDS);check(effects.get()==1,"no replay of unconfirmed scan");
            }finally{one.shutdownNow();}
        }
        System.out.println("OfflineShellProtocolTest OK");
    }
}
