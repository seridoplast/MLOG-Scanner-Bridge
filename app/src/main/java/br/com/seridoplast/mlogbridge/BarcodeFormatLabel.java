package br.com.seridoplast.mlogbridge;

import java.util.Locale;

/** Normaliza SOMENTE a simbologia declarada pelo hardware/servico CMX.
 *  O conteudo nao revela univocamente se e EAN-13, Code 39 ou Code 128.
 */
final class BarcodeFormatLabel {
    static final String UNKNOWN="Não informado pelo CMX";
    private BarcodeFormatLabel(){}

    static String normalize(String value){
        if(value==null || value.trim().isEmpty())return UNKNOWN;
        String original=value.trim();
        String key=original.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]","");
        if(key.isEmpty())return UNKNOWN;
        // Valores inteiros dependem do SDK do fabricante. Nao inventar traducao.
        if(key.matches("[0-9]+"))return "ID "+original+" (tipo sem tabela CMX)";
        if(key.contains("GS1128") || key.contains("EAN128") || key.contains("UCC128"))return "GS1-128";
        if(key.contains("CODE39FULL") || key.contains("CODE39EXT"))return "Code 39 Full ASCII";
        if(key.contains("CODE39") || key.equals("C39") || key.equals("3OF9"))return "Code 39";
        if(key.contains("CODE32"))return "Code 32";
        if(key.contains("CODE128") || key.equals("C128"))return "Code 128";
        if(key.contains("CODE93"))return "Code 93";
        if(key.contains("CODE11"))return "Code 11";
        if(key.contains("EAN13") || key.equals("JAN13"))return "EAN-13";
        if(key.contains("EAN8") || key.equals("JAN8"))return "EAN-8";
        if(key.contains("UPCA"))return "UPC-A";
        if(key.contains("UPCE"))return "UPC-E";
        if(key.contains("MICROQRCODE") || key.equals("MICROQR"))return "Micro QR";
        if(key.contains("QRCODE") || key.equals("QR"))return "QR Code";
        if(key.contains("MICROPDF417"))return "Micro PDF417";
        if(key.contains("PDF417"))return "PDF417";
        if(key.contains("DATAMATRIX") || key.equals("DM"))return "Data Matrix";
        if(key.contains("AZTEC"))return "Aztec";
        if(key.contains("MAXICODE"))return "MaxiCode";
        if(key.contains("HANXIN"))return "Han Xin";
        if(key.contains("DOTCODE"))return "DotCode";
        if(key.contains("GS1DATABAR") || key.contains("GS1RSS") || key.equals("RSS14"))return "GS1 DataBar";
        if(key.contains("MSI"))return "MSI";
        if(key.contains("PLESSEY"))return "Plessey";
        if(key.contains("CODABAR") || key.equals("NW7"))return "Codabar";
        if(key.contains("INTERLEAVED2OF5") || key.equals("ITF") || key.equals("I25"))return "ITF (2 de 5)";
        if(key.contains("MATRIX2OF5"))return "Matrix 2 de 5";
        if(key.contains("INDUSTRIAL2OF5"))return "Industrial 2 de 5";
        if(key.contains("IATA2OF5"))return "IATA 2 de 5";
        return original;
    }
}
