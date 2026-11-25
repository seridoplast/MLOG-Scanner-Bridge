package br.com.seridoplast.mlogbridge;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;

public class LocalAdbPairTransportTest {
    public static void main(String[] args)throws Exception{
        InetAddress loop=InetAddress.getByName("127.0.0.1");
        try(ServerSocket target=new ServerSocket(0,1,loop)){
            ExecutorService pool=Executors.newSingleThreadExecutor();
            try{
                Future<?> server=pool.submit(()->{try(Socket s=target.accept()){
                    int v=s.getInputStream().read();s.getOutputStream().write(v+1);s.getOutputStream().flush();
                }catch(IOException e){throw new RuntimeException(e);}});
                try(LocalAdbPairTransport t=new LocalAdbPairTransport("127.0.0.1",target.getLocalPort(),3000);Socket client=new Socket(loop,t.port())){
                    client.setSoTimeout(2000);client.getOutputStream().write(41);client.getOutputStream().flush();
                    if(client.getInputStream().read()!=42)throw new AssertionError("relay did not forward both directions");
                    if(t.timedOut())throw new AssertionError("unexpected timeout");
                }
                server.get(3,TimeUnit.SECONDS);
            }finally{pool.shutdownNow();}
        }
        try(ServerSocket target=new ServerSocket(0,1,loop);LocalAdbPairTransport t=new LocalAdbPairTransport("127.0.0.1",target.getLocalPort(),500);Socket upstream=target.accept();Socket client=new Socket(loop,t.port())){
            client.setSoTimeout(3000);upstream.setSoTimeout(3000);
            if(client.getInputStream().read()!=-1)throw new AssertionError("client not closed");
            if(upstream.getInputStream().read()!=-1)throw new AssertionError("upstream not closed");
            if(!t.timedOut())throw new AssertionError("deadline not reached");
        }
        try{LocalAdbPairTransport.localAddress("192.0.2.10");throw new AssertionError("remote address allowed");}catch(IOException expected){}
        try{LocalAdbPairTransport.localAddress("example.com");throw new AssertionError("hostname allowed");}catch(IOException expected){}
        System.out.println("LocalAdbPairTransportTest OK: bidirectional relay, deadline, local-only restriction");
    }
}
