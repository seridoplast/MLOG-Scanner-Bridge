package br.com.seridoplast.mlogbridge;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** One-use, loopback-only tunnel for a pairing handshake. The TLS/PAKE
 * is still performed by LibADB; this transport only bounds its socket lifetime.
 */
final class LocalAdbPairTransport implements Closeable {
    private final ServerSocket listener = new ServerSocket();
    private final Socket upstream = new Socket();
    private volatile Socket downstream;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> daemon(r,"adb-pair-deadline"));
    private final ScheduledFuture<?> deadline;
    private volatile boolean timedOut;

    static InetAddress localAddress(String host) throws IOException {
        // Do not resolve arbitrary hostnames or connect to other devices.
        if (host == null || !host.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) throw new IOException("Endereço IPv4 inválido");
        InetAddress address = InetAddress.getByName(host);
        if (!address.isLoopbackAddress() && NetworkInterface.getByInetAddress(address) == null)
            throw new IOException("O endereço não pertence a este coletor");
        return address;
    }
    LocalAdbPairTransport(String host, int port, long timeoutMs) throws IOException {
        if(port<1 || port>65535 || timeoutMs<1)throw new IOException("Porta ou prazo inválido");
        deadline=timer.schedule(()->{timedOut=true;close();},timeoutMs,TimeUnit.MILLISECONDS);
        try {
            listener.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),0),1);
            upstream.connect(new InetSocketAddress(localAddress(host),port),(int)Math.min(4000,timeoutMs));
            upstream.setTcpNoDelay(true);
            daemon(this::accept,"adb-pair-relay").start();
        } catch(IOException | RuntimeException ex){ close(); throw ex; }
    }
    int port(){return listener.getLocalPort();}
    boolean timedOut(){return timedOut;}
    private void accept(){
        try{
            Socket client=listener.accept();downstream=client;
            if(closed.get()){client.close();return;}
            listener.close();client.setTcpNoDelay(true);
            daemon(()->pump(client,upstream),"adb-pair-out").start();
            pump(upstream,client);
        }catch(IOException ignored){close();}
    }
    private void pump(Socket from,Socket to){
        try{
            byte[] bytes=new byte[8192];int n;
            while((n=from.getInputStream().read(bytes))!=-1){to.getOutputStream().write(bytes,0,n);to.getOutputStream().flush();}
            // Preserve the opposite direction until TLS finishes or deadline expires.
            try{to.shutdownOutput();}catch(IOException ignored){}
        }catch(IOException ignored){close();}
    }
    private static Thread daemon(Runnable r,String name){Thread t=new Thread(r,name);t.setDaemon(true);return t;}
    @Override public void close(){
        if(!closed.compareAndSet(false,true))return;
        closeQuietly(listener);closeQuietly(upstream);closeQuietly(downstream);
        timer.shutdownNow();
    }
    private static void closeQuietly(Closeable c){if(c!=null)try{c.close();}catch(IOException ignored){}}
}
