package com.lia.mediaplayer.core.tools;

import java.net.URI;
import java.util.List;

/**
 * Where tools are downloaded from. The official publishers in the game; a local server in
 * tests.
 *
 * @param ytDlpReleases a folder holding yt-dlp's release assets
 * @param ffmpegReleases a folder holding BtbN's ffmpeg build archives
 * @param macFfmpeg evermeet.cx's {@code getrelease} endpoint, for macOS
 */
public record DownloadSources(URI ytDlpReleases, URI ffmpegReleases, URI macFfmpeg) {

    /**
     * The ffmpeg release branch asked for, e.g. {@code n9.0}.
     *
     * <p>BtbN's {@code latest} release holds two families of asset: {@code master} autobuilds,
     * which are whatever ffmpeg's trunk looked like this morning, and per-release-branch
     * builds like this one. Naming a branch is the difference between "the ffmpeg that
     * happened to be built today" and a version this mod has actually been run against.
     * Bump it deliberately. {@link #FFMPEG_FALLBACK_RELEASE} stays {@code master}: BtbN
     * retires a branch's assets some time after the branch itself, and a mod that cannot get
     * ffmpeg at all is worse than one that gets a newer build than it expected.
     */
    static final String FFMPEG_RELEASE = "n9.0";

    static final String FFMPEG_FALLBACK_RELEASE = "master";

    public static DownloadSources official() {
        return new DownloadSources(
                URI.create("https://github.com/yt-dlp/yt-dlp/releases/latest/download/"),
                URI.create("https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/"),
                URI.create("https://evermeet.cx/ffmpeg/getrelease/"));
    }

    /** The single-file yt-dlp release for {@code host}. */
    URI ytDlp(HostSystem host) {
        String asset = switch (host.os()) {
            case WINDOWS -> "yt-dlp.exe";
            case MAC -> "yt-dlp_macos";
            case LINUX -> host.aarch64() ? "yt-dlp_linux_aarch64" : "yt-dlp_linux";
        };
        return ytDlpReleases.resolve(asset);
    }

    /** The ffmpeg+ffprobe archives for Windows or Linux, pinned release first. */
    List<URI> ffmpegArchives(HostSystem host) {
        return List.of(ffmpegArchive(host, FFMPEG_RELEASE), ffmpegArchive(host, FFMPEG_FALLBACK_RELEASE));
    }

    /** evermeet.cx's single-binary zip of {@code tool}, for macOS (an Intel build; Rosetta runs it on ARM). */
    URI macZip(Tool tool) {
        return macFfmpeg.resolve(tool.baseName() + "/zip");
    }

    /**
     * One BtbN asset. A release branch's names repeat its version at the end
     * ({@code ffmpeg-n9.0-latest-linux64-gpl-9.0.tar.xz}); {@code master}'s do not. The
     * {@code -latest} in the middle is BtbN's and means "the newest build of this branch".
     */
    private URI ffmpegArchive(HostSystem host, String release) {
        String versionSuffix = release.startsWith("n") ? "-" + release.substring(1) : "";
        String platform = host.os() == HostSystem.Os.WINDOWS ? "win64" : host.aarch64() ? "linuxarm64" : "linux64";
        String extension = host.os() == HostSystem.Os.WINDOWS ? ".zip" : ".tar.xz";
        return ffmpegReleases.resolve("ffmpeg-" + release + "-latest-" + platform + "-gpl" + versionSuffix + extension);
    }
}
