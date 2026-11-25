package br.com.seridoplast.mlogbridge;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public final class LocalAdbPairInputTest {
    interface Check { void run() throws Exception; }
    static void require(boolean ok) { if(!ok)throw new AssertionError(); }
    static void reject(Check c) throws Exception { try{c.run();throw new AssertionError("Accepted invalid input");}catch(IOException expected){} }
    public static void main(String[] args) throws Exception {
        InetAddress loop=InetAddress.getByName("127.0.0.1");
        InetSocketAddress ep=LocalAdbPairInput.localEndpoint(loop,37123);
        LocalAdbPairInput input=LocalAdbPairInput.automatic(" 012345 ",ep);
        require(input.host.equals("127.0.0.1")&&input.port==37123&&input.code.equals("012345"));
        reject(()->LocalAdbPairInput.automatic("12345",ep));
        reject(()->LocalAdbPairInput.automatic("1234567",ep));
        reject(()->LocalAdbPairInput.automatic("123 456",ep));
        reject(()->LocalAdbPairInput.automatic("１２３４５６",ep));
        reject(()->LocalAdbPairInput.automatic("123456",null));
        require(LocalAdbPairInput.localEndpoint(loop,-1)==null);
        require(LocalAdbPairInput.localEndpoint(loop,65536)==null);
        require(LocalAdbPairInput.localEndpoint(InetAddress.getByName("192.0.2.23"),37123)==null);
        require(LocalAdbPairInput.localEndpoint(InetAddress.getByName("::1"),37123)==null);
        require(LocalAdbPairInput.manual("37123 001234").code.equals("001234"));
        require(LocalAdbPairInput.manual("127.0.0.1:37123 012345").port==37123);
        reject(()->LocalAdbPairInput.manual("192.0.2.23:37123 123456"));
        reject(()->LocalAdbPairInput.manual("65536 123456"));
        reject(()->LocalAdbPairInput.manual("123456"));
        System.out.println("LocalAdbPairInputTest OK");
    }
}
