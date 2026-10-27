package br.com.seridoplast.mlogbridge;

interface IShizukuScanService {
    /** Executes the exact Android shell input text + Enter sequence, in order. */
    boolean injectScan(String code);
}
