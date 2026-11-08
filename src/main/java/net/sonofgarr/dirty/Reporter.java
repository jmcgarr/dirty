package net.sonofgarr.dirty;

import java.io.PrintWriter;

/** Renders a {@link ScanResult}. Implementations are pure: result in, text out. */
public interface Reporter {

    void report(ScanResult result, PrintWriter out);
}
