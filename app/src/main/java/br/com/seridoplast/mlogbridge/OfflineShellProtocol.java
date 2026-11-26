package br.com.seridoplast.mlogbridge;

import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Authenticated loopback RPC. No shell strings, arbitrary keyevents, or remote hosts. */
final class OfflineShellProtocol {
    static final String PING="PING", STOP="STOP";
    private static final SecureRandom RANDOM=new SecureRandom();
    private OfflineShellProtocol(){}
    static byte[] nonce(){byte[] b=new byte[32];RANDOM.nextBytes(b);return b;}
    static String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format(java.util.Locale.ROOT,"%02x",v&255));return s.toString();}
    static byte[] key(String text){if(text==null || !text.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid key");byte[] b=new byte[32];for(int i=0;i<32;i++)b[i]=(byte)Integer.parseInt(text.substring(i*2,i*2+2),16);return b;}
    static boolean valid(String request){
        if(PING.equals(request)||STOP.equals(request))return true;
        if(request==null)return false;
        if(request.startsWith("SCAN:"))return AdbScanMode.supported(request.substring(5));
        if(request.startsWith("VOLUME:"))return request.equals(volume(VolumeRecoveryTarget.x(720),VolumeRecoveryTarget.y(1440),VolumeRecoveryTarget.PAUSE_MS));
        return false;
    }
    static String volume(int x,int y,int ms){return "VOLUME:"+x+":"+y+":"+ms;}
    private static byte[] mac(byte[] key,String role,byte[] client,byte[] server,String message)throws Exception{
        Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(key,"HmacSHA256"));
        m.update(role.getBytes(StandardCharsets.US_ASCII));m.update(client);m.update(server);
        return m.doFinal(message.getBytes(StandardCharsets.US_ASCII));
    }
    private static byte[] read32(DataInputStream in)throws IOException{byte[] b=new byte[32];in.readFully(b);return b;}
    private static boolean same(byte[] a,byte[] b){return MessageDigest.isEqual(a,b);}
    /** Only this exception guarantees that not a single operation byte was sent. */
    static final class BeforeDispatch extends IOException {
        BeforeDispatch(Throwable cause){super("Local service unavailable before dispatch",cause);}
    }
    static boolean call(int port,byte[] key,String request,int timeout)throws Exception{
        if(!valid(request))throw new IllegalArgumentException("Unsupported operation");
        boolean dispatchStarted=false;
        try(Socket s=new Socket()){
            s.connect(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),port),500);
            s.setSoTimeout(timeout);s.setTcpNoDelay(true);
            DataInputStream in=new DataInputStream(s.getInputStream());DataOutputStream out=new DataOutputStream(s.getOutputStream());
            byte[] c=nonce();out.write(c);out.flush();byte[] n=read32(in);
            if(!same(read32(in),mac(key,"server-v1",c,n,"")))throw new IOException("Local service not authenticated");
            byte[] req=request.getBytes(StandardCharsets.US_ASCII);byte[] signature=mac(key,"request-v1",c,n,request);
            dispatchStarted=true;out.writeByte(req.length);out.write(req);out.write(signature);out.flush();
            int result=in.readUnsignedByte();if(result>1 || !same(read32(in),mac(key,"response-v1",c,n,request+":"+result)))throw new IOException("Unconfirmed response");
            return result==1;
        }catch(Exception ex){if(!dispatchStarted)throw new BeforeDispatch(ex);throw ex;}
    }
    interface Handler {boolean run(String request)throws Exception;}
    /** Returns an authenticated operation or null for a rejected client. */
    static String serve(Socket socket,byte[] key,Handler handler)throws Exception{
        socket.setSoTimeout(1200);socket.setTcpNoDelay(true);
        DataInputStream in=new DataInputStream(socket.getInputStream());DataOutputStream out=new DataOutputStream(socket.getOutputStream());
        byte[] c=read32(in),n=nonce();out.write(n);out.write(mac(key,"server-v1",c,n,""));out.flush();
        int len=in.readUnsignedByte();if(len==0 || len>90)return null;
        byte[] req=new byte[len];in.readFully(req);String request=new String(req,StandardCharsets.US_ASCII);
        if(!valid(request)||!same(read32(in),mac(key,"request-v1",c,n,request)))return null;
        boolean ok=false;try{ok=handler.run(request);}catch(Exception ignored){}
        int result=ok?1:0;out.writeByte(result);out.write(mac(key,"response-v1",c,n,request+":"+result));out.flush();return request;
    }
}
