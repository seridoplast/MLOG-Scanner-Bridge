package br.com.seridoplast.mlogbridge;

/** Only an explicitly identified order-entry screen may trigger an observation. */
final class OrderNumberPolicy {
    private OrderNumberPolicy(){}
    static String normalize(String raw){
        if(raw==null)return null;
        // Trim only scanner transport suffixes. Never remove leading zeroes.
        String code=raw.trim();
        if(code.isEmpty() || code.length()>72)return null;
        for(int i=0;i<code.length();i++){
            char ch=code.charAt(i);
            if(!((ch>='0'&&ch<='9')||(ch>='a'&&ch<='z')||
                 (ch>='A'&&ch<='Z')||ch=='-'||ch=='/'||ch=='_'))return null;
        }
        return code;
    }
    static boolean isOrderScreen(String screen){return "pedidos".equals(screen);}
}
