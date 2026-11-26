package br.com.seridoplast.mlogbridge;

import java.io.*;
import java.net.*;
import java.util.concurrent.TimeUnit;

/** Started by the already authenticated ADB shell (UID 2000), not root.
 * Lives in a separate session; Android/firmware may still terminate it.
 * Bound exclusively to IPv4 loopback; only authenticated fixed operations.
 */
public final class OfflineShellAgent {
    private static final long LEASE_MS=180_000L;
    private OfflineShellAgent(){}
    public static void main(String[] args)throws Exception{
        if(android.os.Process.myUid()!=2000 || args.length!=1)return;
        int port=Integer.parseInt(args[0]);if(port<30000 || port>55000)return;
        String secret=new BufferedReader(new InputStreamReader(System.in,java.nio.charset.StandardCharsets.US_ASCII)).readLine();
        byte[] key=OfflineShellProtocol.key(secret);
        try(ServerSocket server=new ServerSocket()){
            server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),port),4);server.setSoTimeout(1000);
            android.util.Log.i("BridgeOffline","Agente UID 2000 ativo em loopback");
            long last=android.os.SystemClock.elapsedRealtime();
            while(android.os.SystemClock.elapsedRealtime()-last<LEASE_MS){
                try(Socket client=server.accept()){
                    String request=OfflineShellProtocol.serve(client,key,OfflineShellAgent::run);
                    if(request!=null){last=android.os.SystemClock.elapsedRealtime();if(OfflineShellProtocol.STOP.equals(request))return;}
                }catch(IOException ignored){}catch(Exception ignored){}
            }
        }finally{java.util.Arrays.fill(key,(byte)0);android.util.Log.i("BridgeOffline","Agente local encerrado");}
    }
    private static boolean run(String request)throws Exception{
        if(OfflineShellProtocol.PING.equals(request)||OfflineShellProtocol.STOP.equals(request))return true;
        if(request.startsWith("SCAN:") && OfflineShellProtocol.valid(request)){
            if(!input("text",request.substring(5)))return false;
            Thread.sleep(110);return input("keyevent","66");
        }
        if(OfflineShellProtocol.valid(request)&&request.startsWith("VOLUME:")){
            if(!input("tap",Integer.toString(VolumeRecoveryTarget.x(720)),Integer.toString(VolumeRecoveryTarget.y(1440))))return false;
            Thread.sleep(VolumeRecoveryTarget.PAUSE_MS);
            return input("tap",Integer.toString(VolumeRecoveryTarget.BACK_X),Integer.toString(VolumeRecoveryTarget.BACK_Y));
        }
        return false;
    }
    private static boolean input(String... args)throws Exception{
        String[] cmd=new String[args.length+1];cmd[0]="/system/bin/input";System.arraycopy(args,0,cmd,1,args.length);
        Process p=new ProcessBuilder(cmd).redirectOutput(new File("/dev/null")).redirectError(new File("/dev/null")).start();
        try{if(!p.waitFor(2500,TimeUnit.MILLISECONDS))return false;return p.exitValue()==0;}finally{if(p.isAlive())p.destroyForcibly();}
    }
}
