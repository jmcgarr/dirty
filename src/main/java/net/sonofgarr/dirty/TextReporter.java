package net.sonofgarr.dirty;

import picocli.CommandLine.Help;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Help.Ansi.Style;
import picocli.CommandLine.Help.ColorScheme;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * One line per repository that needs attention:
 * {@code <path>: <n> modified, <m> untracked No remote repository}.
 * With {@code all}, clean repositories are listed too, as {@code <path>: clean}.
 */
public final class TextReporter implements Reporter {

    private final ColorScheme colors;
    private final boolean all;

    public TextReporter(Ansi ansi) {
        this(ansi, false);
    }

    public TextReporter(Ansi ansi, boolean all) {
        this.colors = new ColorScheme.Builder().ansi(ansi).build();
        this.all = all;
    }

    /** Styles are applied programmatically; repo names and git messages are never parsed as markup. */
    private String paint(String text, Style style) {
        return colors.apply(text, List.of(style)).toString();
    }

    @Override
    public void report(ScanResult result, PrintWriter out) {
        for (RepoReport report : result.reports()) {
            if (all || report.needsAttention()) {
                out.println(format(report));
            }
        }
        out.flush();
    }

    String format(RepoReport report) {
        StringBuilder line = new StringBuilder(paint(report.displayName() + ":", Style.fg_yellow));
        if (report.hasError()) {
            return line.append(' ').append(paint("error: " + firstLine(report.error()), Style.fg_red)).toString();
        }
        RepoStatus status = report.status();
        if (!status.isDirty()) {
            return line.append(' ').append(paint("clean", Style.fg_green)).toString();
        }
        List<String> counts = new ArrayList<>();
        if (status.modified() > 0) {
            counts.add(status.modified() + " modified");
        }
        if (status.untracked() > 0) {
            counts.add(status.untracked() + " untracked");
        }
        if (!counts.isEmpty()) {
            line.append(' ').append(paint(String.join(", ", counts), Style.fg_green));
        }
        if (!status.hasRemote()) {
            line.append(' ').append(paint("No remote repository", Style.fg_red));
        }
        return line.toString();
    }

    private static String firstLine(String text) {
        return text.lines().findFirst().orElse("").strip();
    }
}
