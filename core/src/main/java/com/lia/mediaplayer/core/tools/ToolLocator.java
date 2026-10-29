package com.lia.mediaplayer.core.tools;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finds existing installations of a tool. It never downloads anything — that is
 * {@link ToolDownloader}'s job.
 *
 * <p>Places are checked in order:
 *
 * <ol>
 *   <li>an explicit override ({@code -Dliasmediaplayer.<tool>=…} or the matching
 *       {@code *_PATH} environment variable);
 *   <li>the copy the mod downloaded into its {@code bin/} folder;
 *   <li>every directory on {@code PATH};
 *   <li>common per-OS install locations (winget, scoop, chocolatey, Homebrew, …);
 *   <li>the bare command name, trusting the launcher's own {@code PATH}.
 * </ol>
 *
 * <p>A candidate is usable when it runs and prints a version; ffmpeg and ffprobe must also be
 * at least 8.1.2, the floor v2 set.
 */
public final class ToolLocator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ToolLocator.class);
    static final String MIN_FFMPEG_TEXT = "8.1.2";
    private static final int[] MIN_FFMPEG = {8, 1, 2};
    private static final Duration VERSION_TIMEOUT = Duration.ofSeconds(15);
    private static final Pattern FFMPEG_VERSION = Pattern.compile("version\\s+n?(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    private final HostSystem host;
    private final Path binDir;
    private final ProcessRunner runner;

    public ToolLocator(HostSystem host, Path binDir, ProcessRunner runner) {
        this.host = host;
        this.binDir = binDir;
        this.runner = runner;
    }

    /** Where a downloaded copy of {@code tool} lives. */
    public Path managedPath(Tool tool) {
        return binDir.resolve(tool.exeName(host));
    }

    /** The first usable copy of {@code tool}, if there is one. Blocking. */
    public Optional<LocatedTool> find(Tool tool) {
        for (Path candidate : candidates(tool)) {
            if (isExecutableFile(candidate)) {
                Optional<LocatedTool> usable = check(tool, candidate);
                if (usable.isPresent()) {
                    return usable;
                }
            }
        }
        return check(tool, Path.of(tool.exeName(host)));
    }

    /** {@code path} as a copy of {@code tool}, if it runs and is recent enough. Blocking. */
    public Optional<LocatedTool> check(Tool tool, Path path) {
        ProcessResult result;
        try {
            result = runner.run(List.of(path.toString(), tool.versionFlag()), VERSION_TIMEOUT, 1 << 20);
        } catch (IOException e) {
            return Optional.empty();
        }
        if (!result.succeeded()) {
            return Optional.empty();
        }
        String version = firstLine(result.stdout());
        if (version.isEmpty()) {
            return Optional.empty();
        }
        if (tool.isFfmpegBundle() && !isRecentFfmpeg(version)) {
            LOGGER.info("Ignoring {} at {}: older than {}", version, path, MIN_FFMPEG_TEXT);
            return Optional.empty();
        }
        return Optional.of(new LocatedTool(tool, path, version));
    }

    /** Every absolute path checked for {@code tool}, in order, without duplicates. */
    public List<Path> candidates(Tool tool) {
        var paths = new LinkedHashSet<Path>();
        String exe = tool.exeName(host);

        String override = nonBlank(host.property(tool.overrideProperty()));
        for (String variable : tool.overrideEnvironment()) {
            if (override == null) {
                override = nonBlank(host.env(variable));
            }
        }
        if (override != null) {
            paths.add(Path.of(override.strip()));
        }

        paths.add(managedPath(tool));

        String pathVariable = host.env("PATH");
        if (pathVariable != null) {
            String separator = host.os() == HostSystem.Os.WINDOWS ? ";" : File.pathSeparator;
            for (String dir : pathVariable.split(Pattern.quote(separator), -1)) {
                if (!dir.isBlank()) {
                    paths.add(Path.of(dir.strip(), exe));
                }
            }
        }

        Path home = host.home();
        if (host.os() == HostSystem.Os.WINDOWS) {
            String localAppData = firstNonBlank(
                    host.env("LOCALAPPDATA"), home.resolve("AppData/Local").toString());
            String appData = firstNonBlank(
                    host.env("APPDATA"), home.resolve("AppData/Roaming").toString());
            paths.add(Path.of(localAppData, "Microsoft", "WinGet", "Links", exe));
            paths.add(home.resolve("scoop/shims").resolve(exe));
            paths.add(Path.of("C:\\ProgramData\\chocolatey\\bin", exe));
            if (tool == Tool.YT_DLP) {
                // pip and pip --user put it in a Python "Scripts" folder.
                paths.addAll(pythonScripts(Path.of(localAppData, "Programs", "Python"), exe));
                paths.addAll(pythonScripts(Path.of(appData, "Python"), exe));
            }
        } else {
            for (String dir : List.of("/usr/local/bin", "/usr/bin", "/bin", "/opt/homebrew/bin", "/snap/bin")) {
                paths.add(Path.of(dir, exe));
            }
            paths.add(home.resolve(".local/bin").resolve(exe));
            paths.add(home.resolve("bin").resolve(exe));
        }
        return List.copyOf(paths);
    }

    /**
     * Whether an {@code ffmpeg -version} first line names a version at least the floor.
     * A line without a recognisable version (a git build, a distribution's own scheme) is
     * accepted: refusing it would only stop a working tool.
     */
    static boolean isRecentFfmpeg(String versionLine) {
        var matcher = FFMPEG_VERSION.matcher(versionLine);
        if (!matcher.find()) {
            return true;
        }
        int[] found = {
            Integer.parseInt(matcher.group(1)),
            Integer.parseInt(matcher.group(2)),
            matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3))
        };
        for (int i = 0; i < found.length; i++) {
            if (found[i] != MIN_FFMPEG[i]) {
                return found[i] > MIN_FFMPEG[i];
            }
        }
        return true;
    }

    static boolean isExecutableFile(Path path) {
        return Files.isRegularFile(path) && Files.isExecutable(path);
    }

    private static List<Path> pythonScripts(Path base, String exe) {
        var out = new ArrayList<Path>();
        if (!Files.isDirectory(base)) {
            return out;
        }
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(base, "Python*")) {
            for (Path dir : dirs) {
                out.add(dir.resolve("Scripts").resolve(exe));
            }
        } catch (IOException e) {
            // Best effort: a folder we cannot list has nothing we can run.
        }
        return out;
    }

    private static String firstLine(String text) {
        return text.strip().lines().findFirst().orElse("").strip();
    }

    @Nullable
    private static String nonBlank(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String firstNonBlank(@Nullable String value, String fallback) {
        String present = nonBlank(value);
        return present != null ? present : fallback;
    }
}
