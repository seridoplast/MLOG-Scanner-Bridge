package br.com.seridoplast.mlogbridge;

/**
 * Profile0: reader firmware delivers keyboard output, including a newline.
 * The Bridge does NOT receive+retype scans in this mode.
 * Exact Zebra DataWedge implementation is proprietary; this selects the
 * closest output provided by the CMX/Hikrobot hardware.
 */
final class ZebraProfile0Mode {
    static final String PREF = "zebra_profile0_native_keyboard_v329";
    static final String OUTPUT = "keyboard";
    static final String TERMINATOR = "newline";
    static final String SCAN_MODE = "trigger";
    private ZebraProfile0Mode() {}
    static boolean isSelected(boolean zebra, boolean profile0) {
        return zebra && profile0;
    }
    static boolean usesBridgeIme(boolean zebra, boolean profile0) {
        return zebra && !profile0;
    }
}
