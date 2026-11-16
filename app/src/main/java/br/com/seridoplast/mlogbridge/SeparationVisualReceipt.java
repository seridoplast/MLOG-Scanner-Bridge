package br.com.seridoplast.mlogbridge;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evidence of a VISIBLE quantity change in the MLOG separation card.
 * This is NOT proof of server persistence; it only helps recognize a UI reaction.
 * Product and units must match before comparing two observations.
 */
final class SeparationVisualReceipt {
    private static final Pattern PRODUCT = Pattern.compile("(?s)^\\s*([0-9]{2,})\\s*[-–]");
    private static final Pattern SEPARATED = Pattern.compile(
        "(?is)([0-9]+)\\s*(KG|CX|PC|UN|UND|UNID|PÇ|PÇS)\\s*SEPARADO(?:S|AS)?");
    private static final Pattern FRACTIONATED = Pattern.compile(
        "(?is)([0-9]+)\\s*(KG|CX|PC|UN|UND|UNID|PÇ|PÇS)\\s*FRACIONADO(?:S|AS)?");

    final String product;
    final String separatedUnit;
    final int separated;
    final String fractionatedUnit;
    final int fractionated;

    private SeparationVisualReceipt(String product, String separatedUnit, int separated,
                                    String fractionatedUnit, int fractionated) {
        this.product=product;
        this.separatedUnit=separatedUnit;
        this.separated=separated;
        this.fractionatedUnit=fractionatedUnit;
        this.fractionated=fractionated;
    }

    static SeparationVisualReceipt parse(CharSequence card) {
        if(card==null)return null;
        String s=card.toString();
        if(!s.toUpperCase(Locale.ROOT).contains("SEPARAR"))return null;
        Matcher p=PRODUCT.matcher(s), a=SEPARATED.matcher(s), b=FRACTIONATED.matcher(s);
        if(!p.find() || (!a.find() && !b.find()))return null;
        // Do not let the short-circuit above affect matcher state.
        a.reset(); b.reset();
        boolean foundA=a.find(),foundB=b.find();
        if(!foundA && !foundB)return null;
        try {
            return new SeparationVisualReceipt(p.group(1),
                foundA ? a.group(2).toUpperCase(Locale.ROOT) : "",
                foundA ? Integer.parseInt(a.group(1)) : -1,
                foundB ? b.group(2).toUpperCase(Locale.ROOT) : "",
                foundB ? Integer.parseInt(b.group(1)) : -1);
        } catch(NumberFormatException e) { return null; }
    }

    boolean sameCounters(SeparationVisualReceipt other) {
        return other!=null && product.equals(other.product) &&
            separatedUnit.equals(other.separatedUnit) &&
            fractionatedUnit.equals(other.fractionatedUnit);
    }

    boolean increased(SeparationVisualReceipt next) {
        if(!sameCounters(next))return false;
        boolean separatedUp=separated>=0 && next.separated>separated;
        boolean fractionatedUp=fractionated>=0 && next.fractionated>fractionated;
        boolean separatedDown=separated>=0 && next.separated<separated;
        boolean fractionatedDown=fractionated>=0 && next.fractionated<fractionated;
        // Never accept a simultaneous counter regression.
        return (separatedUp || fractionatedUp) && !separatedDown && !fractionatedDown;
    }
}
