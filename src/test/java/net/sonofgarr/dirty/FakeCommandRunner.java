package net.sonofgarr.dirty;

import net.sonofgarr.dirty.process.CommandResult;
import net.sonofgarr.dirty.process.CommandRunner;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Canned results keyed by the git subcommand (everything after {@code git} and its global flags). */
final class FakeCommandRunner implements CommandRunner {

    final List<List<String>> invocations = new ArrayList<>();
    final List<Map<String, String>> environments = new ArrayList<>();
    private final Map<String, CommandResult> results = new HashMap<>();
    private IOException failure;

    FakeCommandRunner on(String subcommand, int exitCode, String stdout, String stderr) {
        results.put(subcommand, new CommandResult(exitCode, stdout, stderr));
        return this;
    }

    FakeCommandRunner on(String subcommand, String stdout) {
        return on(subcommand, 0, stdout, "");
    }

    FakeCommandRunner failingWith(IOException failure) {
        this.failure = failure;
        return this;
    }

    @Override
    public CommandResult run(Path workingDirectory, List<String> command, Map<String, String> environment) throws IOException {
        invocations.add(List.copyOf(command));
        environments.add(new HashMap<>(environment));
        if (failure != null) {
            throw failure;
        }
        String key = String.join(" ", command.stream().skip(1).filter(a -> !a.startsWith("--no-optional-locks")).toList());
        CommandResult result = results.get(key);
        if (result == null) {
            throw new AssertionError("no canned result for: " + key);
        }
        return result;
    }
}
