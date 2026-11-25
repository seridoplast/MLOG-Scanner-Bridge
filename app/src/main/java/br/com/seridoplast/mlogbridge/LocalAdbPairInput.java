package br.com.seridoplast.mlogbridge;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;

/** Stateless validation; no pairing code is stored in preferences or logs. */
final class LocalAdbPairInput {
    final String host;
    final int port;
    final String code;
    private LocalAdbPairInput(String host,int port,String code) { this.host=host; this.port=port; this.code=code; }
    static InetSocketAddress localEndpoint(InetAddress address,int port) {
        if (!(address instanceof Inet4Address) || port < 1 || port > 65535) return null;
        try { return new InetSocketAddress(LocalAdbPairTransport.localAddress(address.getHostAddress()),port); }
        catch (IOException e) { return null; }
    }
    static LocalAdbPairInput automatic(String value,InetSocketAddress endpoint) throws IOException {
        String code=value==null?"":value.trim();
        if (!code.matches("[0-9]{6}")) throw new IOException("Informe somente os seis dígitos do código.");
        if (endpoint==null || localEndpoint(endpoint.getAddress(),endpoint.getPort())==null) throw new IOException("Porta de pareamento indisponível. Abra um novo código.");
        return new LocalAdbPairInput(endpoint.getAddress().getHostAddress(),endpoint.getPort(),code);
    }
    static LocalAdbPairInput manual(String value) throws IOException {
        String[] parts=(value==null?"":value.trim()).split("\\s+");
        if (parts.length!=2 || !parts[0].matches("(?:[0-9]{1,3}(?:\\.[0-9]{1,3}){3}:)?[0-9]{1,5}")) throw new IOException("Use IP:porta e código, separados por um espaço.");
        String[] ep=parts[0].split(":");
        InetAddress address=LocalAdbPairTransport.localAddress(ep.length==2?ep[0]:"127.0.0.1");
        int port=Integer.parseInt(ep[ep.length-1]);
        return automatic(parts[1],localEndpoint(address,port));
    }
}
