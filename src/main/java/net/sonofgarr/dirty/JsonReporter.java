package net.sonofgarr.dirty;

import java.io.PrintWriter;
import java.util.List;

/**
 * Every discovered repository as one JSON document, version 1:
 * <pre>{"version": 1, "root": "...", "repos": [{"path", "dirty", "modified", "untracked", "hasRemote", "error"}]}</pre>
 * Status fields are {@code null} on error rows; {@code error} is {@code null} otherwise. Pretty-printed with
 * two-space indentation and {@code \n} line endings on every platform, so consumers see identical bytes.
 */
public final class JsonReporter implements Reporter {

    /** Bumped only for a change that removes or renames a key; additions keep the version. */
    static final int VERSION = 1;

    @Override
    public void report(ScanResult result, PrintWriter out) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"version\": ").append(VERSION).append(",\n");
        json.append("  \"root\": ").append(quote(result.root().toString())).append(",\n");
        List<RepoReport> reports = result.reports();
        if (reports.isEmpty()) {
            json.append("  \"repos\": []\n");
        } else {
            json.append("  \"repos\": [\n");
            for (int i = 0; i < reports.size(); i++) {
                appendRepo(json, reports.get(i));
                json.append(i < reports.size() - 1 ? "},\n" : "}\n");
            }
            json.append("  ]\n");
        }
        json.append("}\n");
        out.print(json);
        out.flush();
    }

    private static void appendRepo(StringBuilder json, RepoReport report) {
        json.append("    {\n");
        json.append("      \"path\": ").append(quote(report.displayName())).append(",\n");
        if (report.hasError()) {
            json.append("      \"dirty\": null,\n");
            json.append("      \"modified\": null,\n");
            json.append("      \"untracked\": null,\n");
            json.append("      \"hasRemote\": null,\n");
            json.append("      \"error\": ").append(quote(report.error().strip())).append('\n');
        } else {
            RepoStatus status = report.status();
            json.append("      \"dirty\": ").append(status.isDirty()).append(",\n");
            json.append("      \"modified\": ").append(status.modified()).append(",\n");
            json.append("      \"untracked\": ").append(status.untracked()).append(",\n");
            json.append("      \"hasRemote\": ").append(status.hasRemote()).append(",\n");
            json.append("      \"error\": null\n");
        }
        json.append("    ");
    }

    /** A JSON string literal: quotes, backslashes and control characters escaped; everything else passes through. */
    static String quote(String text) {
        StringBuilder sb = new StringBuilder(text.length() + 2).append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
