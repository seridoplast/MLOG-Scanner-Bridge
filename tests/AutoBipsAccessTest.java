package br.com.seridoplast.mlogbridge;
public final class AutoBipsAccessTest {
    public static void main(String[] args){
        if(!AutoBipsAccess.valid("211010Ef@"))throw new AssertionError("expected password accepted");
        if(AutoBipsAccess.valid("211010ef@"))throw new AssertionError("password must be case-sensitive");
        if(AutoBipsAccess.valid("211010Ef"))throw new AssertionError("wrong password accepted");
        if(AutoBipsAccess.valid(null))throw new AssertionError("null password accepted");
        System.out.println("AutoBipsAccessTest OK");
    }
}
