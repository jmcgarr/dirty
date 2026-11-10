package net.sonofgarr.dirty;

import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Help.Ansi.Style;
import picocli.CommandLine.Help.ColorScheme;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Aligned columns {@code REPO  MODIFIED  UNTRACKED  REMOTE  ERROR}, one row per repository that needs
 * attention (every repository with {@code all}). Prints nothing for zero rows. Cells are padded first and
 * colored afterwards, so widths are measured on plain text; the last cell of a row is never padded.
 */
public final class TableReporter implements Reporter {

    private static final String[] HEADER = {"REPO", "MODIFIED", "UNTRACKED", "REMOTE", "ERROR"};
    private static final String SEPARATOR = "  ";

    private final ColorScheme colors;
    private final boolean all;

    public TableReporter(Ansi ansi, boolean all) {
        this.colors = new ColorScheme.Builder().ansi(ansi).build();
        this.all = all;
    }

    @Override
    public void report(ScanResult result, PrintWriter out) {
        List<RepoReport> shown = result.reports().stream()
                .filter(report -> all || report.needsAttention())
                .toList();
        if (shown.isEmpty()) {
            out.flush();
            return;
        }
        List<String[]> rows = new ArrayList<>();
        rows.add(HEADER);
        shown.forEach(report -> rows.add(cells(report)));
        int[] widths = new int[HEADER.length];
        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                widths[i] = Math.max(widths[i], row[i].length());
            }
        }
        out.println(render(HEADER, widths, new Style[HEADER.length]));
        for (RepoReport report : shown) {
            out.println(render(cells(report), widths, styles(report)));
        }
        out.flush();
    }

    private static String[] cells(RepoReport report) {
        if (report.hasError()) {
            return new String[] {report.displayName(), "-", "-", "-", firstLine(report.error())};
        }
        RepoStatus status = report.status();
        return new String[] {
                report.displayName(),
                Integer.toString(status.modified()),
                Integer.toString(status.untracked()),
                status.hasRemote() ? "yes" : "no",
                ""
        };
    }

    private static Style[] styles(RepoReport report) {
        if (report.hasError()) {
            return new Style[] {Style.fg_yellow, Style.fg_red, Style.fg_red, Style.fg_red, Style.fg_red};
        }
        RepoStatus status = report.status();
        return new Style[] {
                Style.fg_yellow,
                status.modified() > 0 ? Style.fg_green : null,
                status.untracked() > 0 ? Style.fg_green : null,
                status.hasRemote() ? null : Style.fg_red,
                null
        };
    }

    private String render(String[] cells, int[] widths, Style[] styles) {
        int last = cells.length - 1;
        while (last > 0 && cells[last].isEmpty()) {
            last--;
        }
        StringBuilder line = new StringBuilder();
        for (int i = 0; i <= last; i++) {
            if (i > 0) {
                line.append(SEPARATOR);
            }
            String cell = i == last ? cells[i] : pad(cells[i], widths[i], i == 1 || i == 2);
            line.append(styles[i] == null ? cell : colors.apply(cell, List.of(styles[i])).toString());
        }
        return line.toString();
    }

    private static String pad(String text, int width, boolean rightAlign) {
        String padding = " ".repeat(width - text.length());
        return rightAlign ? padding + text : text + padding;
    }

    private static String firstLine(String text) {
        return text.lines().findFirst().orElse("").strip();
    }
}
