package com.lia.mediaplayer.core.ffmpeg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lia.mediaplayer.api.source.StreamUrl;
import com.lia.mediaplayer.core.net.Http;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FfmpegCommandTest {

    private static final StreamUrl VIDEO = StreamUrl.of("https://cdn.example/v.mp4");
    private static final StreamUrl AUDIO = StreamUrl.of("https://cdn.example/a.m4a");

    private static SessionPlan plan(Optional<StreamUrl> audio, boolean video, boolean sound, Duration start) {
        var headers = new LinkedHashMap<String, String>();
        headers.put("User-Agent", "site-agent");
        headers.put("Referer", "https://site.example/");
        return new SessionPlan(VIDEO, audio, headers, video, sound, start, 1280, 720, 29.97);
    }

    @Test
    void oneProcessReadsSeparatePictureAndSound() {
        List<String> command = FfmpegCommand.session(
                "ffmpeg", plan(Optional.of(AUDIO), true, true, Duration.ofMillis(90_500)), "tcp://127.0.0.1:4000");

        assertThat(command).startsWith("ffmpeg", "-nostdin", "-hide_banner", "-loglevel", "error");
        // Both inputs seek, carry the site's headers and are restricted to network protocols.
        assertThat(command.stream().filter("-i"::equals)).hasSize(2);
        assertThat(command).containsSubsequence("-ss", "90.500", "-i", VIDEO.url(), "-ss", "90.500", "-i", AUDIO.url());
        assertThat(command).containsSubsequence("-user_agent", "site-agent");
        assertThat(command).containsSubsequence("-headers", "Referer: https://site.example/\r\n");
        assertThat(command.stream().filter(FfmpegCommand.PROTOCOL_WHITELIST::equals))
                .hasSize(2);
        assertThat(FfmpegCommand.PROTOCOL_WHITELIST).doesNotContain("file");
        // The picture as constant-rate PAM on stdout, the sound from the second input.
        assertThat(command)
                .containsSubsequence(
                        "-map",
                        "0:v:0",
                        "-vf",
                        "scale=1280:720:force_original_aspect_ratio=decrease:flags=bilinear",
                        "-fps_mode",
                        "cfr",
                        "-r",
                        "29.97",
                        "-pix_fmt",
                        "rgba",
                        "-f",
                        "image2pipe",
                        "-c:v",
                        "pam",
                        "pipe:1")
                .containsSubsequence(
                        "-map",
                        "1:a:0",
                        "-ac",
                        "2",
                        "-ar",
                        "48000",
                        "-c:a",
                        "pcm_s16le",
                        "-f",
                        "s16le",
                        "tcp://127.0.0.1:4000")
                .endsWith("tcp://127.0.0.1:4000");
    }

    @Test
    void soundFromTheOnlyInputAndNoSeekAtTheStart() {
        List<String> command =
                FfmpegCommand.session("ffmpeg", plan(Optional.empty(), false, true, Duration.ZERO), "tcp://[::1]:9");
        assertThat(command).doesNotContain("-ss", "pipe:1", "-vf").containsSubsequence("-map", "0:a:0");
        assertThatThrownBy(
                        () -> FfmpegCommand.session("ffmpeg", plan(Optional.empty(), true, true, Duration.ZERO), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void probesWithTheDefaultAgentAndTheUrlAfterDashI() {
        List<String> command = FfmpegCommand.probe("ffprobe", VIDEO, Map.of());
        assertThat(command)
                .startsWith("ffprobe")
                .endsWith("-i", VIDEO.url())
                .containsSubsequence("-user_agent", Http.USER_AGENT);
        assertThat(command).doesNotContain("-headers");
    }

    @Test
    void writesRatesWithAsFewDigitsAsTheyNeed() {
        assertThat(FfmpegCommand.rate(30)).isEqualTo("30");
        assertThat(FfmpegCommand.rate(29.97002997)).isEqualTo("29.97");
        assertThat(FfmpegCommand.rate(23.976)).isEqualTo("23.976");
    }

    @Test
    void plansRefuseWhatCannotPlay() {
        assertThatThrownBy(() -> plan(Optional.empty(), false, false, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> plan(Optional.empty(), true, false, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> new SessionPlan(VIDEO, Optional.empty(), Map.of(), true, false, Duration.ZERO, 1, 1, 30))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(plan(Optional.empty(), true, false, Duration.ZERO).frameIntervalMicros())
                .isBetween(33_366.0, 33_367.0);
    }

    @Test
    void readsPamHeaders() throws IOException {
        byte[] stream = "P7\nWIDTH 405\nHEIGHT 720\nDEPTH 4\nMAXVAL 255\nTUPLTYPE RGB_ALPHA\nENDHDR\n"
                .getBytes(StandardCharsets.US_ASCII);
        var header = PamReader.readHeader(new ByteArrayInputStream(stream));
        assertThat(header).isEqualTo(new PamReader.Header(405, 720, 4));
        assertThat(header.frameBytes()).isEqualTo(405 * 720 * 4);
        assertThat(PamReader.readHeader(new ByteArrayInputStream(new byte[0]))).isNull();
    }

    @Test
    void refusesPamItCannotUse() {
        for (String bad : List.of(
                "P6\n",
                "P7\nWIDTH 10\nHEIGHT 10\nDEPTH 3\nENDHDR\n",
                "P7\nWIDTH 0\nHEIGHT 10\nDEPTH 4\nENDHDR\n",
                "P7\nWIDTH 99999999\nHEIGHT 10\nDEPTH 4\nENDHDR\n",
                "P7\nWIDTH x\n",
                "P7\nWIDTH \n",
                "P7\nCOMMENT " + "x".repeat(200) + "\n")) {
            assertThatThrownBy(() ->
                            PamReader.readHeader(new ByteArrayInputStream(bad.getBytes(StandardCharsets.US_ASCII))))
                    .as(bad)
                    .isInstanceOf(IOException.class);
        }
        assertThatThrownBy(() -> PamReader.readHeader(
                        new ByteArrayInputStream("P7\nWIDTH 4".getBytes(StandardCharsets.US_ASCII))))
                .isInstanceOf(EOFException.class);
    }

    @Test
    void readsProbes() throws IOException {
        ProbeResult song = ProbeResult.parse("""
                {"streams": [
                   {"codec_type": "audio", "sample_rate": "44100", "channels": 2},
                   {"codec_type": "video", "width": 600, "height": 600, "avg_frame_rate": "0/0",
                    "disposition": {"attached_pic": 1}}],
                 "format": {"duration": "187.42"}}
                """);
        assertThat(song.hasAudio()).isTrue();
        assertThat(song.hasVideo()).as("album art is not a picture track").isFalse();
        assertThat(song.duration()).contains(Duration.ofMillis(187_420));

        ProbeResult clip = ProbeResult.parse("""
                {"streams": [{"codec_type": "video", "width": 1920, "height": 1080,
                              "avg_frame_rate": "30000/1001", "r_frame_rate": "60/1"}],
                 "format": {"duration": "N/A"}}
                """);
        assertThat(clip.hasVideo()).isTrue();
        assertThat(clip.hasAudio()).isFalse();
        assertThat(clip.fps()).isBetween(29.96, 29.98);
        assertThat(clip.duration()).isEmpty();

        assertThat(ProbeResult.rate("")).isZero();
        assertThat(ProbeResult.rate("25")).isEqualTo(25);
        assertThat(ProbeResult.rate("x/y")).isZero();
        assertThat(ProbeResult.parse("{\"streams\": [{\"codec_type\": \"video\", \"r_frame_rate\": \"1000/1\"}]}")
                        .fps())
                .isZero();
    }

    @Test
    void bracketsIpv6LoopbackForUrls() throws IOException {
        assertThat(Session.hostLiteral(InetAddress.getByAddress(new byte[] {127, 0, 0, 1})))
                .isEqualTo("127.0.0.1");
        assertThat(Session.hostLiteral(
                        InetAddress.getByAddress(new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1})))
                .isEqualTo("[0:0:0:0:0:0:0:1]");
    }
}
