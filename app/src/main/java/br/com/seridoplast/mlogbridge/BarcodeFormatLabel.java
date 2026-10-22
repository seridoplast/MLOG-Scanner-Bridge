package br.com.seridoplast.mlogbridge;

import java.util.Locale;

/** Normaliza SOMENTE o formato informado pelo proprio scanner (sem inferencias). */
final class BarcodeFormatLabel {
    static final String UNKNOWN = "Nao informado pelo CMX";

    private BarcodeFormatLabel() {}

    static String normalize(String value) {
        if (value == null || value.trim().isEmpty()) return UNKNOWN;
        String original=value.trim();
        String key=original.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (key.contains("CODE39") || key.equals("C39")) return "Code 39";
        if (key.contains("CODE128") || key.equals("C128")) return "Code 128";
        if (key.contains("CODE93")) return "Code 93";
        if (key.contains("EAN13")) return "EAN-13";
        if (key.contains("EAN8")) return "EAN-8";
        if (key.contains("UPCA")) return "UPC-A";
        if (key.contains("UPCE")) return "UPC-E";
        if (key.contains("QRCODE") || key.equals("QR")) return "QR Code";
        if (key.contains("DATAMATRIX")) return "Data Matrix";
        if (key.contains("PDF417")) return "PDF417";
        if (key.equals("ITF") || key.contains("INTERLEAVED2OF5")) return "ITF (2 de 5)";
        if (key.contains("CODABAR")) return "Codabar";
        return original;
    }
}
