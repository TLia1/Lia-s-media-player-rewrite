package com.lia.mediaplayer.core.tools;

import java.util.List;

/** The external programs the engine runs. */
public enum Tool {
    /** Resolves web pages (YouTube, SoundCloud, …) to stream URLs. */
    YT_DLP("yt-dlp", "--version", "liasmediaplayer.ytdlp", List.of("YT_DLP_PATH", "YTDLP_PATH")),
    /** Decodes every video and audio stream. */
    FFMPEG("ffmpeg", "-version", "liasmediaplayer.ffmpeg", List.of("FFMPEG_PATH")),
    /** Reads the geometry and tracks of direct files before they play. */
    FFPROBE("ffprobe", "-version", "liasmediaplayer.ffprobe", List.of("FFPROBE_PATH"));

    private final String baseName;
    private final String versionFlag;
    private final String overrideProperty;

    @SuppressWarnings("ImmutableEnumChecker") // always a List.of
    private final List<String> overrideEnvironment;

    Tool(String baseName, String versionFlag, String overrideProperty, List<String> overrideEnvironment) {
        this.baseName = baseName;
        this.versionFlag = versionFlag;
        this.overrideProperty = overrideProperty;
        this.overrideEnvironment = overrideEnvironment;
    }

    /** {@code "yt-dlp"}, {@code "ffmpeg"}, {@code "ffprobe"}. */
    public String baseName() {
        return baseName;
    }

    /** The executable's file name on {@code host}, e.g. {@code yt-dlp.exe} on Windows. */
    public String exeName(HostSystem host) {
        return baseName + host.exeSuffix();
    }

    /** The flag that makes the program print its version and exit 0. */
    String versionFlag() {
        return versionFlag;
    }

    /** The JVM property that names an explicit path, e.g. {@code -Dliasmediaplayer.ffmpeg=…}. */
    String overrideProperty() {
        return overrideProperty;
    }

    /** Environment variables that name an explicit path, checked after the property. */
    List<String> overrideEnvironment() {
        return overrideEnvironment;
    }

    /** ffmpeg and ffprobe come in one archive and are installed together. */
    boolean isFfmpegBundle() {
        return this == FFMPEG || this == FFPROBE;
    }
}
