package net.sonofgarr.dirty;

import java.io.PrintWriter;

/**
 * Every discovered repository as one CSV row under a fixed header. RFC 4180 quoting, but {@code \n} line
 * endings rather than {@code \r\n}: Unix text tools trip over the carriage return, while spreadsheets and
 * Python's {@code csv} accept either. Error rows leave the four status cells empty.
 */
public final class CsvReporter implements Reporter {

    static final String HEADER = "path,dirty,modified,untracked,hasRemote,error";

    @Override
    public void report(ScanResult result, PrintWriter out) {
        StringBuilder csv = new StringBuilder(HEADER).append('\n');
        for (RepoReport report : result.reports()) {
            csv.append(quote(report.displayName())).append(',');
            if (report.hasError()) {
                csv.append(",,,,").append(quote(report.error().strip()));
            } else {
                RepoStatus status = report.status();
                csv.append(status.isDirty()).append(',')
                        .append(status.modified()).append(',')
                        .append(status.untracked()).append(',')
                        .append(status.hasRemote()).append(',');
            }
            csv.append('\n');
        }
        out.print(csv);
        out.flush();
    }

    /** Wraps the field in quotes only when it contains a comma, quote, CR or LF; embedded quotes are doubled. */
    static String quote(String field) {
        boolean needsQuoting = false;
        for (int i = 0; i < field.length() && !needsQuoting; i++) {
            char c = field.charAt(i);
            needsQuoting = c == ',' || c == '"' || c == '\r' || c == '\n';
        }
        return needsQuoting ? '"' + field.replace("\"", "\"\"") + '"' : field;
    }
}
