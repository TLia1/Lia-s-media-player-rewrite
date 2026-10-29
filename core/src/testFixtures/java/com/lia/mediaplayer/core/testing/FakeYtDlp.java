package com.lia.mediaplayer.core.testing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;

/**
 * A stand-in for yt-dlp: a shell script that records its arguments and prints canned output.
 * POSIX only; tests using it skip themselves on Windows.
 */
public final class FakeYtDlp {

    private final Path script;
    private final Path argumentsLog;

    private FakeYtDlp(Path script, Path argumentsLog) {
        this.script = script;
        this.argumentsLog = argumentsLog;
    }

    public static boolean isSupported() {
        return !System.getProperty("os.name", "")
                .toLowerCase(java.util.Locale.ROOT)
                .contains("win");
    }

    /** A yt-dlp that prints {@code stdout}, writes {@code stderr} and exits with {@code exitCode}. */
    public static FakeYtDlp create(Path dir, String stdout, String stderr, int exitCode) throws IOException {
        Path out = Files.writeString(dir.resolve("yt-dlp.out"), stdout, StandardCharsets.UTF_8);
        Path err = Files.writeString(dir.resolve("yt-dlp.err"), stderr, StandardCharsets.UTF_8);
        Path log = dir.resolve("yt-dlp.args");
        Path script = dir.resolve("yt-dlp");
        Files.writeString(
                script,
                "#!/bin/sh\n"
                        + "for a in \"$@\"; do printf '%s\\n' \"$a\"; done > '" + log + "'\n"
                        + "if [ \"$1\" = \"--version\" ]; then echo 2026.10.01; exit 0; fi\n"
                        + "cat '" + out + "'\n"
                        + "cat '" + err + "' >&2\n"
                        + "exit " + exitCode + "\n",
                StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
        return new FakeYtDlp(script, log);
    }

    public Path path() {
        return script;
    }

    /** The arguments of the last run, one per element. */
    public List<String> lastArguments() throws IOException {
        return Files.exists(argumentsLog) ? Files.readAllLines(argumentsLog) : List.of();
    }
}
