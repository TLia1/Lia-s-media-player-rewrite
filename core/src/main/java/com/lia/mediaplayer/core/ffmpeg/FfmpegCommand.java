package com.lia.mediaplayer.core.ffmpeg;

import com.lia.mediaplayer.api.source.StreamUrl;
import com.lia.mediaplayer.core.net.Http;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Builds ffmpeg and ffprobe command lines. Pure: the strings it returns are the whole contract
 * with the tools, so they are unit-tested as they are.
 *
 * <p>Every input is a remote http(s) URL, given after {@code -i} so it can never be read as an
 * option, and every input is limited to network protocols: a remote HLS playlist lists its own
 * segment URLs, and without the whitelist it could point ffmpeg at local files.
 */
public final class FfmpegCommand {

    /** Network protocols a remote stream needs. No {@code file}. */
    static final String PROTOCOL_WHITELIST = "http,https,tcp,tls,crypto,hls,dash,data";

    private FfmpegCommand() {}

    /**
     * One playback session: the picture as PAM frames on stdout, the sound as PCM to
     * {@code audioTarget}.
     *
     * <p>PAM rather than raw video because each frame carries its own width and height: ffmpeg
     * decides the output size (rotation metadata, non-square pixels, an adaptive stream
     * changing resolution) and says what it decided, so no probe has to predict it. The header
     * is about 70 bytes and costs nothing measurable next to the frame.
     *
     * <p>{@code -fps_mode cfr -r} makes the output rate constant, so frame {@code n} is due
     * exactly {@code n / fps} after the start, even for phone videos recorded at a variable
     * rate. No {@code -re}: ffmpeg reads ahead as fast as the network allows and the rings hold
     * it back, which is the jitter cushion; the picture follows the audio clock.
     *
     * @param audioTarget where the sound goes, e.g. {@code tcp://127.0.0.1:41234}; required
     *     when the plan writes sound
     */
    public static List<String> session(String ffmpeg, SessionPlan plan, @Nullable String audioTarget) {
        var command = new ArrayList<>(List.of(ffmpeg, "-nostdin", "-hide_banner", "-loglevel", "error"));
        addInput(command, plan.primary(), plan.headers(), plan.start().toNanos() / 1e9);
        plan.separateAudio()
                .ifPresent(audio ->
                        addInput(command, audio, plan.headers(), plan.start().toNanos() / 1e9));

        if (plan.video()) {
            command.addAll(List.of(
                    "-map",
                    "0:v:0",
                    "-vf",
                    // Bilinear: every frame is scaled here at frame rate, and then again by the
                    // GPU into a window that is smaller still. Bicubic's extra taps buy nothing
                    // visible after the second step.
                    "scale=" + plan.maxWidth() + ":" + plan.maxHeight()
                            + ":force_original_aspect_ratio=decrease:flags=bilinear",
                    "-fps_mode",
                    "cfr",
                    "-r",
                    rate(plan.fps()),
                    "-pix_fmt",
                    "rgba",
                    "-f",
                    "image2pipe",
                    "-c:v",
                    "pam",
                    "pipe:1"));
        }
        if (plan.audio()) {
            if (audioTarget == null) {
                throw new IllegalArgumentException("The plan writes sound but no target was given");
            }
            String audioInput = plan.separateAudio().isPresent() ? "1" : "0";
            command.addAll(List.of(
                    "-map",
                    audioInput + ":a:0",
                    "-ac",
                    "2",
                    "-ar",
                    "48000",
                    "-c:a",
                    "pcm_s16le",
                    "-f",
                    "s16le",
                    audioTarget));
        }
        return command;
    }

    /** {@code ffprobe} printing the streams and container of {@code url} as JSON. */
    public static List<String> probe(String ffprobe, StreamUrl url, Map<String, String> headers) {
        var command = new ArrayList<>(
                List.of(ffprobe, "-v", "error", "-print_format", "json", "-show_format", "-show_streams"));
        addNetworkOptions(command, headers);
        command.add("-i");
        command.add(url.url());
        return command;
    }

    private static void addInput(
            List<String> command, StreamUrl url, Map<String, String> headers, double startSeconds) {
        addNetworkOptions(command, headers);
        command.addAll(List.of("-reconnect", "1", "-reconnect_streamed", "1", "-reconnect_delay_max", "5"));
        if (startSeconds > 0) {
            command.add("-ss");
            command.add(String.format(Locale.ROOT, "%.3f", startSeconds));
        }
        command.add("-i");
        command.add(url.url());
    }

    private static void addNetworkOptions(List<String> command, Map<String, String> headers) {
        command.add("-protocol_whitelist");
        command.add(PROTOCOL_WHITELIST);
        String userAgent = Http.USER_AGENT;
        var extra = new StringBuilder();
        for (var header : headers.entrySet()) {
            if (header.getKey().equalsIgnoreCase("User-Agent")) {
                userAgent = header.getValue();
            } else {
                extra.append(header.getKey())
                        .append(": ")
                        .append(header.getValue())
                        .append("\r\n");
            }
        }
        command.add("-user_agent");
        command.add(userAgent);
        if (!extra.isEmpty()) {
            command.add("-headers");
            command.add(extra.toString());
        }
        // 15 s without a byte is a dead connection, in microseconds.
        command.add("-rw_timeout");
        command.add("15000000");
    }

    /** {@code 30}, {@code 29.97}: as few digits as the rate needs. */
    static String rate(double fps) {
        if (fps == Math.rint(fps)) {
            return Long.toString((long) fps);
        }
        return String.format(Locale.ROOT, "%.3f", fps).replaceAll("0+$", "");
    }
}
