package com.lia.mediaplayer.core.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.lia.mediaplayer.core.event.EventBus;
import com.lia.mediaplayer.core.net.Http;
import com.lia.mediaplayer.core.testing.FakeYtDlp;
import com.lia.mediaplayer.core.testing.MediaServer;
import com.lia.mediaplayer.core.testing.TestClientThread;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ToolsTest {

    @TempDir
    Path dir;

    private final TestClientThread client = new TestClientThread();
    private final ProcessRunner runner = new ProcessRunner(client);
    private final Http http = new Http(client);

    @AfterEach
    void close() {
        http.close();
        runner.close();
        client.close();
    }

    private static HostSystem host(
            HostSystem.Os os, Map<String, String> properties, Map<String, String> env, Path home) {
        return new HostSystem(os, false, properties::get, env::get, home);
    }

    /** A script that prints {@code version} like a tool asked for its version. */
    private Path fakeTool(Path folder, String name, String version) throws IOException {
        Files.createDirectories(folder);
        Path script = folder.resolve(name);
        Files.writeString(script, "#!/bin/sh\necho '" + version + "'\n", StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
        return script;
    }

    @Test
    void looksInTheDocumentedOrderOnLinux() {
        Path home = Path.of("/home/me");
        var host = host(
                HostSystem.Os.LINUX,
                Map.of("liasmediaplayer.ffmpeg", "/opt/custom/ffmpeg"),
                Map.of("PATH", "/a:/b:", "FFMPEG_PATH", "/ignored/ffmpeg"),
                home);
        var locator = new ToolLocator(host, Path.of("/game/liasmediaplayer/bin"), runner);

        assertThat(locator.candidates(Tool.FFMPEG))
                .startsWith(
                        Path.of("/opt/custom/ffmpeg"),
                        Path.of("/game/liasmediaplayer/bin/ffmpeg"),
                        Path.of("/a/ffmpeg"),
                        Path.of("/b/ffmpeg"),
                        Path.of("/usr/local/bin/ffmpeg"))
                .contains(home.resolve(".local/bin/ffmpeg"))
                .doesNotHaveDuplicates();
        var envOnly = new ToolLocator(
                host(HostSystem.Os.LINUX, Map.of(), Map.of("YTDLP_PATH", "/env/yt-dlp"), home), dir, runner);
        assertThat(envOnly.candidates(Tool.YT_DLP)).startsWith(Path.of("/env/yt-dlp"));
    }

    @Test
    void looksInTheUsualWindowsPlaces() {
        var host = host(
                HostSystem.Os.WINDOWS,
                Map.of(),
                Map.of("LOCALAPPDATA", "C:\\Users\\me\\AppData\\Local"),
                Path.of("home"));
        var locator = new ToolLocator(host, Path.of("bin"), runner);
        assertThat(locator.candidates(Tool.YT_DLP))
                .contains(
                        Path.of("bin", "yt-dlp.exe"),
                        Path.of("C:\\Users\\me\\AppData\\Local", "Microsoft", "WinGet", "Links", "yt-dlp.exe"),
                        Path.of("home").resolve("scoop/shims").resolve("yt-dlp.exe"));
        assertThat(Tool.YT_DLP.exeName(host)).isEqualTo("yt-dlp.exe");
    }

    @Test
    void acceptsRecentFfmpegAndUnreadableVersions() {
        assertThat(ToolLocator.isRecentFfmpeg("ffmpeg version n9.0.2 Copyright (c) 2000-2026"))
                .isTrue();
        assertThat(ToolLocator.isRecentFfmpeg("ffmpeg version 8.1.2-static")).isTrue();
        assertThat(ToolLocator.isRecentFfmpeg("ffmpeg version 8.2 Copyright")).isTrue();
        assertThat(ToolLocator.isRecentFfmpeg("ffmpeg version 8.1.1")).isFalse();
        assertThat(ToolLocator.isRecentFfmpeg("ffmpeg version 6.1.1-3ubuntu5")).isFalse();
        assertThat(ToolLocator.isRecentFfmpeg("ffmpeg version N-117000-g1234abcd"))
                .isTrue();
    }

    @Test
    void findsTheFirstUsableCopy() throws IOException {
        assumeTrue(FakeYtDlp.isSupported(), "uses shell scripts");
        Path old = fakeTool(dir.resolve("old"), "ffmpeg", "ffmpeg version 6.0 Copyright");
        Path good = fakeTool(dir.resolve("good"), "ffmpeg", "ffmpeg version 9.0 Copyright");
        var host = host(HostSystem.Os.LINUX, Map.of(), Map.of("PATH", old.getParent() + ":" + good.getParent()), dir);
        var locator = new ToolLocator(host, dir.resolve("bin"), runner);

        assertThat(locator.find(Tool.FFMPEG)).hasValueSatisfying(found -> {
            assertThat(found.path()).isEqualTo(good);
            assertThat(found.command()).isEqualTo(good.toString());
            assertThat(found.version()).isEqualTo("ffmpeg version 9.0 Copyright");
        });
        assertThat(locator.check(Tool.FFMPEG, dir.resolve("missing"))).isEmpty();
    }

    @Test
    void recognisesExecutablesByTheirFirstBytes() {
        byte[] elf = {0x7F, 'E', 'L', 'F'};
        byte[] pe = {'M', 'Z', 0, 0};
        byte[] universal = {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE};
        byte[] html = {'<', 'h', 't', 'm'};
        assertThat(ToolDownloader.hasExecutableMagic(elf, HostSystem.Os.LINUX)).isTrue();
        assertThat(ToolDownloader.hasExecutableMagic(pe, HostSystem.Os.WINDOWS)).isTrue();
        assertThat(ToolDownloader.hasExecutableMagic(universal, HostSystem.Os.MAC))
                .isTrue();
        assertThat(ToolDownloader.hasExecutableMagic(pe, HostSystem.Os.LINUX)).isFalse();
        assertThat(ToolDownloader.hasExecutableMagic(html, HostSystem.Os.MAC)).isFalse();
        assertThat(ToolDownloader.hasExecutableMagic(new byte[2], HostSystem.Os.WINDOWS))
                .isFalse();
    }

    @Test
    void refusesArchiveEntriesThatEscape() throws IOException {
        assertThat(ToolDownloader.safeResolve(dir, "ffmpeg/bin/ffmpeg").startsWith(dir))
                .isTrue();
        assertThatThrownBy(() -> ToolDownloader.safeResolve(dir, "../../etc/cron.d/x"))
                .hasMessageContaining("outside the archive");
        Path zip = zip(dir.resolve("evil.zip"), Map.of("../evil", new byte[] {1}));
        assertThatThrownBy(() -> ToolDownloader.extractZip(zip, dir.resolve("out")))
                .isInstanceOf(IOException.class);
    }

    @Test
    void downloadsYtDlpAndRejectsAnErrorPage() throws IOException {
        try (var server = new MediaServer()) {
            byte[] elf = new byte[150_000];
            elf[0] = 0x7F;
            elf[1] = 'E';
            elf[2] = 'L';
            elf[3] = 'F';
            server.serve("/releases/yt-dlp_linux", Files.write(dir.resolve("asset"), elf));
            var host = host(HostSystem.Os.LINUX, Map.of(), Map.of(), dir);
            var sources = new DownloadSources(
                    URI.create(server.url("/releases/")),
                    URI.create(server.url("/ffmpeg/")),
                    URI.create(server.url("/mac/")));
            Path bin = dir.resolve("bin");
            var downloader = new ToolDownloader(http, host, bin, sources, runner);
            var progress = new CopyOnWriteArrayList<Long>();

            Path installed = downloader.downloadYtDlp((bytes, total) -> progress.add(bytes));

            assertThat(installed).isEqualTo(bin.resolve("yt-dlp"));
            assertThat(Files.size(installed)).isEqualTo(150_000);
            assertThat(Files.isExecutable(installed)).isTrue();
            assertThat(progress).isNotEmpty().last().isEqualTo(150_000L);
            try (var listing = Files.list(bin)) {
                assertThat(listing).containsExactly(installed);
            }

            server.serve("/releases/yt-dlp_linux", Files.write(dir.resolve("page"), new byte[200_000]));
            assertThatThrownBy(() -> downloader.downloadYtDlp(Http.Progress.NONE))
                    .hasMessageContaining("is not a LINUX executable");
            server.fail("/releases/yt-dlp_linux", 404);
            assertThatThrownBy(() -> downloader.downloadYtDlp(Http.Progress.NONE))
                    .hasMessageContaining("HTTP 404");
        }
    }

    @Test
    void installsFfmpegAndFfprobeFromOneArchiveFallingBackToMaster() throws IOException {
        try (var server = new MediaServer()) {
            // Random, so the archive does not compress below the size of a real one.
            byte[] exe = new byte[700_000];
            new Random(1).nextBytes(exe);
            exe[0] = 'M';
            exe[1] = 'Z';
            Path archive = zip(
                    dir.resolve("ffmpeg.zip"),
                    Map.of(
                            "ffmpeg-master-latest-win64-gpl/bin/ffmpeg.exe",
                            exe,
                            "ffmpeg-master-latest-win64-gpl/bin/ffprobe.exe",
                            exe,
                            "ffmpeg-master-latest-win64-gpl/LICENSE.txt",
                            new byte[10]));
            // The pinned release is gone; master is there.
            server.serve("/ffmpeg/ffmpeg-master-latest-win64-gpl.zip", archive);
            var host = host(HostSystem.Os.WINDOWS, Map.of(), Map.of(), dir);
            var sources = new DownloadSources(
                    URI.create(server.url("/yt/")),
                    URI.create(server.url("/ffmpeg/")),
                    URI.create(server.url("/mac/")));
            Path bin = dir.resolve("bin");

            new ToolDownloader(http, host, bin, sources, runner).downloadFfmpeg(Http.Progress.NONE);

            assertThat(bin.resolve("ffmpeg.exe")).exists();
            assertThat(bin.resolve("ffprobe.exe")).exists();
            assertThat(server.hits("/ffmpeg/ffmpeg-n9.0-latest-win64-gpl-9.0.zip"))
                    .isEqualTo(1);
            try (var listing = Files.list(bin)) {
                assertThat(listing).hasSize(2);
            }
        }
    }

    @Test
    void theManagerKeepsAWorkingYtDlpWhenItsReplacementIsNotAProgram() throws Exception {
        assumeTrue(FakeYtDlp.isSupported(), "uses shell scripts");
        Path path = dir.resolve("path");
        fakeTool(path, "ffmpeg", "ffmpeg version 9.0");
        fakeTool(path, "ffprobe", "ffprobe version 9.0");
        fakeTool(path, "yt-dlp", "2020.01.01");
        try (var server = new MediaServer();
                var io = Executors.newVirtualThreadPerTaskExecutor()) {
            // The stale yt-dlp is replaced by a download that is a script, not an ELF program.
            Path newer = fakeTool(dir.resolve("assets"), "yt-dlp_linux", "2026.10.20");
            byte[] bytes = Files.readAllBytes(newer);
            byte[] padded = new byte[120_000];
            System.arraycopy(bytes, 0, padded, 0, bytes.length);
            server.serve("/releases/yt-dlp_linux", Files.write(dir.resolve("asset"), padded));
            var host = host(HostSystem.Os.LINUX, Map.of(), Map.of("PATH", path.toString()), dir);
            var sources = new DownloadSources(
                    URI.create(server.url("/releases/")), URI.create(server.url("/f/")), URI.create(server.url("/m/")));
            var states = new CopyOnWriteArrayList<ToolState>();
            var bus = new EventBus(client);
            bus.subscribe(ToolState.class, states::add);
            Path bin = dir.resolve("bin");

            var manager = new ToolManager(
                    new ToolLocator(host, bin, runner),
                    new ToolDownloader(http, host, bin, sources, runner),
                    bus,
                    io,
                    new ToolManager.Policy(true, true),
                    () -> LocalDate.of(2026, 10, 29));
            ToolState state = manager.start().get();

            assertThat(state).isInstanceOf(ToolState.Ready.class);
            assertThat(manager.require(Tool.FFMPEG).path()).isEqualTo(path.resolve("ffmpeg"));
            assertThat(manager.require(Tool.YT_DLP).version()).isEqualTo("2020.01.01");
            assertThat(manager.start().get()).isSameAs(state);
            client.run(() -> {});
            assertThat(states).first().isInstanceOf(ToolState.Locating.class);
            assertThat(states).anyMatch(s -> s instanceof ToolState.Downloading d && d.tool() == Tool.YT_DLP);
            assertThat(states).last().isEqualTo(state);
        }
    }

    @Test
    void theManagerSaysWhereItLookedWhenAToolIsMissing() throws Exception {
        try (var io = Executors.newVirtualThreadPerTaskExecutor()) {
            var host = host(
                    HostSystem.Os.LINUX,
                    Map.of(),
                    Map.of("PATH", dir.resolve("empty").toString()),
                    dir);
            var manager = new ToolManager(
                    new ToolLocator(host, dir.resolve("bin"), runner),
                    new ToolDownloader(http, host, dir.resolve("bin"), DownloadSources.official(), runner),
                    new EventBus(client),
                    io,
                    new ToolManager.Policy(false, false),
                    () -> LocalDate.of(2026, 10, 29));
            // Make sure the bare commands are not found either.
            assumeTrue(new ToolLocator(host, dir, runner)
                    .check(Tool.YT_DLP, Path.of("yt-dlp"))
                    .isEmpty());

            var missing = (ToolState.Missing) manager.start().get();

            assertThat(missing.checked()).containsKey(Tool.YT_DLP);
            assertThat(missing.checked().get(Tool.YT_DLP))
                    .contains(dir.resolve("bin").resolve("yt-dlp"));
            assertThatThrownBy(() -> manager.require(Tool.YT_DLP))
                    .hasMessageContaining("-Dliasmediaplayer.ytdlp=")
                    .isInstanceOfSatisfying(
                            ToolMissingException.class,
                            e -> assertThat(e.tool()).isEqualTo(Tool.YT_DLP));
        }
    }

    @Test
    void ytDlpGoesStaleAfterAMonth() {
        LocalDate today = LocalDate.of(2026, 10, 29);
        assertThat(ToolManager.isStale("2026.10.01", today)).isFalse();
        assertThat(ToolManager.isStale("2026.09.28", today)).isTrue();
        assertThat(ToolManager.isStale("2026.09.29", today)).isFalse();
        assertThat(ToolManager.isStale("2026.02.31", today)).isFalse();
        assertThat(ToolManager.isStale("stable@2025.01.01", today)).isTrue();
        assertThat(ToolManager.isStale("unknown", today)).isFalse();
    }

    @Test
    void fixedProvidersHandOutWhatTheyWereGiven() throws ToolMissingException {
        var ffmpeg = new LocatedTool(Tool.FFMPEG, Path.of("ffmpeg"), "9");
        var provider = ToolProvider.fixed(Map.of(Tool.FFMPEG, ffmpeg));
        assertThat(provider.require(Tool.FFMPEG)).isSameAs(ffmpeg);
        assertThatThrownBy(() -> provider.require(Tool.FFPROBE)).isInstanceOf(ToolMissingException.class);
    }

    private static Path zip(Path target, Map<String, byte[]> entries) throws IOException {
        try (OutputStream file = Files.newOutputStream(target);
                var zip = new ZipOutputStream(file)) {
            for (var entry : new HashMap<>(entries).entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return target;
    }
}
