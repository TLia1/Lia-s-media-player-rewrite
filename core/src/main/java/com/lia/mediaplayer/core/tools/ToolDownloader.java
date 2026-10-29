package com.lia.mediaplayer.core.tools;

import com.lia.mediaplayer.core.net.Http;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Downloads and installs tools into the mod's {@code bin/} folder. Finding existing copies
 * is {@link ToolLocator}'s job.
 *
 * <p>yt-dlp is one self-contained executable. ffmpeg and ffprobe come together in one
 * archive per platform: a {@code .zip} on Windows, a {@code .tar.xz} on Linux (unpacked by
 * the system {@code tar}), and two single-binary zips on macOS. Every download goes to a
 * temporary file and is moved into place atomically, so a failed one never leaves a broken
 * binary behind.
 *
 * <h2>Trust model</h2>
 *
 * <p>The mod downloads executables and then runs them, so it owes an account of what that
 * trust rests on. It rests on <b>TLS to three named publishers</b>, and on nothing else:
 *
 * <ul>
 *   <li>{@code github.com/yt-dlp/yt-dlp}, the upstream project's own release assets;
 *   <li>{@code github.com/BtbN/FFmpeg-Builds}, the build most ffmpeg documentation points
 *       Windows and Linux users at;
 *   <li>{@code evermeet.cx}, the equivalent for macOS.
 * </ul>
 *
 * <p>There is deliberately <b>no checksum check</b>, and that is a limitation rather than an
 * oversight: none of the three publishes a checksum that is any more trustworthy than the
 * asset beside it (same host, same TLS, same release), so verifying one would describe a
 * guarantee the mod does not have. A hash baked into the mod would pin the bytes, but only
 * until the next build replaces them — which for yt-dlp is the point of the tool.
 *
 * <p>What is checked is what can be: the response is a 200, the body is large enough to be a
 * real binary rather than an error page, it starts with the magic bytes of an executable for
 * this platform ({@link #hasExecutableMagic}), and archives cannot write outside their
 * extraction directory ({@link #safeResolve}).
 *
 * <p>ffmpeg is pinned to a release branch ({@link DownloadSources#FFMPEG_RELEASE}). yt-dlp
 * deliberately follows {@code latest}: it breaks whenever YouTube changes its player, and
 * being current <em>is</em> its function.
 */
public final class ToolDownloader {

    private static final Logger LOGGER = LoggerFactory.getLogger(ToolDownloader.class);
    private static final Duration TIMEOUT = Duration.ofMinutes(5);
    private static final long MIN_YT_DLP_BYTES = 100_000;
    private static final long MIN_FFMPEG_BYTES = 1_000_000;
    private static final long MAX_BYTES = 512L * 1024 * 1024;
    private static final Duration TAR_TIMEOUT = Duration.ofMinutes(2);

    private final Http http;
    private final HostSystem host;
    private final Path binDir;
    private final DownloadSources sources;
    private final ProcessRunner runner;

    public ToolDownloader(Http http, HostSystem host, Path binDir, DownloadSources sources, ProcessRunner runner) {
        this.http = http;
        this.host = host;
        this.binDir = binDir;
        this.sources = sources;
        this.runner = runner;
    }

    /** Downloads yt-dlp, replacing any copy in {@code bin/}, and returns its path. Blocking. */
    public Path downloadYtDlp(Http.Progress progress) throws IOException {
        Files.createDirectories(binDir);
        Path target = binDir.resolve(Tool.YT_DLP.exeName(host));
        URI source = sources.ytDlp(host);
        Path part = Files.createTempFile(binDir, "yt-dlp", ".part");
        try {
            LOGGER.info("Downloading yt-dlp from {}", source);
            http.download(source, part, MIN_YT_DLP_BYTES, MAX_BYTES, TIMEOUT, progress);
            requireExecutable(part);
            makeExecutable(part);
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(part);
        }
        LOGGER.info("yt-dlp ready at {}", target);
        return target;
    }

    /** Downloads ffmpeg and ffprobe together, replacing any copies in {@code bin/}. Blocking. */
    public void downloadFfmpeg(Http.Progress progress) throws IOException {
        Files.createDirectories(binDir);
        if (host.os() == HostSystem.Os.MAC) {
            for (Tool tool : List.of(Tool.FFMPEG, Tool.FFPROBE)) {
                installFromArchive(sources.macZip(tool), ".zip", List.of(tool), progress);
            }
            return;
        }
        IOException last = null;
        for (URI archive : sources.ffmpegArchives(host)) {
            try {
                installFromArchive(archive, archiveSuffix(archive), List.of(Tool.FFMPEG, Tool.FFPROBE), progress);
                return;
            } catch (IOException e) {
                LOGGER.warn("Could not install ffmpeg from {}: {}", archive, e.toString());
                last = e;
            }
        }
        throw last != null ? last : new IOException("No ffmpeg archive for this platform");
    }

    private void installFromArchive(URI source, String suffix, List<Tool> tools, Http.Progress progress)
            throws IOException {
        Path archive = Files.createTempFile(binDir, "ffmpeg", suffix);
        Path unpacked = Files.createTempDirectory(binDir, "ffmpeg-unpack");
        try {
            LOGGER.info("Downloading ffmpeg from {}", source);
            http.download(source, archive, MIN_FFMPEG_BYTES, MAX_BYTES, TIMEOUT, progress);
            if (suffix.equals(".zip")) {
                extractZip(archive, unpacked);
            } else {
                extractTar(archive, unpacked);
            }
            for (Tool tool : tools) {
                String exe = tool.exeName(host);
                Path found = findFile(unpacked, exe)
                        .orElseThrow(() -> new IOException("'" + exe + "' is not in the archive from " + source));
                requireExecutable(found);
                makeExecutable(found);
                Files.move(found, binDir.resolve(exe), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(archive);
            deleteRecursively(unpacked);
        }
        LOGGER.info("ffmpeg ready in {}", binDir);
    }

    static void extractZip(Path archive, Path destination) throws IOException {
        try (var zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path out = safeResolve(destination, entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Path parent = out.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    try (OutputStream file = Files.newOutputStream(out)) {
                        zip.transferTo(file);
                    }
                }
                zip.closeEntry();
            }
        }
    }

    /**
     * Unpacks a {@code .tar.xz} with the system {@code tar}, which reads xz on every Linux
     * desktop. A Java xz library would be a dependency for this one call. GNU tar refuses
     * members that climb out of the destination unless asked not to.
     */
    private void extractTar(Path archive, Path destination) throws IOException {
        var result = runner.run(
                List.of("tar", "-xf", archive.toString(), "-C", destination.toString()), TAR_TIMEOUT, 1 << 20);
        if (!result.succeeded()) {
            throw new IOException("tar failed (exit " + result.exitCode() + "): " + result.stderr());
        }
    }

    /** {@code entryName} under {@code destination}, refusing an entry that would escape it (zip-slip). */
    static Path safeResolve(Path destination, String entryName) throws IOException {
        Path root = destination.toAbsolutePath().normalize();
        Path resolved = root.resolve(entryName).normalize();
        if (!resolved.startsWith(root)) {
            throw new IOException("Refusing to extract an entry outside the archive folder: " + entryName);
        }
        return resolved;
    }

    private void requireExecutable(Path file) throws IOException {
        byte[] head;
        try (InputStream in = Files.newInputStream(file)) {
            head = in.readNBytes(4);
        }
        if (!hasExecutableMagic(head, host.os())) {
            throw new IOException(file.getFileName() + " is not a " + host.os() + " executable");
        }
    }

    /**
     * Whether {@code head} starts like a program {@code os} can run.
     *
     * <p>It does not make an unknown binary safe — nothing here can, see the trust model —
     * but it catches the failures that need no attacker: a publisher moving an asset, a
     * captive portal answering 200 with a login page, an archive whose layout changed.
     *
     * <ul>
     *   <li>Windows: {@code MZ}, the DOS header every PE still carries.
     *   <li>Linux: {@code 0x7F E L F}.
     *   <li>macOS: Mach-O in either byte order, or a universal binary ({@code 0xCAFEBABE}),
     *       which is what evermeet.cx ships.
     * </ul>
     */
    static boolean hasExecutableMagic(byte[] head, HostSystem.Os os) {
        if (head.length < 4) {
            return false;
        }
        int b0 = head[0] & 0xFF;
        int b1 = head[1] & 0xFF;
        int b2 = head[2] & 0xFF;
        int b3 = head[3] & 0xFF;
        return switch (os) {
            case WINDOWS -> b0 == 'M' && b1 == 'Z';
            case LINUX -> b0 == 0x7F && b1 == 'E' && b2 == 'L' && b3 == 'F';
            case MAC -> {
                long word = ((long) b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
                yield word == 0xFEEDFACEL
                        || word == 0xFEEDFACFL
                        || word == 0xCEFAEDFEL
                        || word == 0xCFFAEDFEL
                        || word == 0xCAFEBABEL
                        || word == 0xBEBAFECAL;
            }
        };
    }

    private void makeExecutable(Path file) throws IOException {
        if (host.os() == HostSystem.Os.WINDOWS) {
            return;
        }
        try {
            Files.setPosixFilePermissions(
                    file,
                    EnumSet.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE,
                            PosixFilePermission.GROUP_READ,
                            PosixFilePermission.GROUP_EXECUTE,
                            PosixFilePermission.OTHERS_READ,
                            PosixFilePermission.OTHERS_EXECUTE));
        } catch (UnsupportedOperationException e) {
            if (!file.toFile().setExecutable(true)) {
                throw new IOException("Cannot make " + file + " executable", e);
            }
        }
    }

    private static Optional<Path> findFile(Path root, String name) throws IOException {
        try (var files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase(name))
                    .findFirst();
        }
    }

    private static String archiveSuffix(URI uri) {
        return uri.getPath().toLowerCase(Locale.ROOT).endsWith(".zip") ? ".zip" : ".tar.xz";
    }

    private static void deleteRecursively(Path dir) {
        try (var files = Files.walk(dir)) {
            files.sorted(Comparator.reverseOrder())
                    .forEach(path -> path.toFile().delete());
        } catch (IOException e) {
            LOGGER.debug("Could not clean up {}: {}", dir, e.toString());
        }
    }
}
