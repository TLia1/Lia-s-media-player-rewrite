package com.lia.mediaplayer.api.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.source.MediaMetadata.Tracks;
import com.lia.mediaplayer.api.text.Text;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SourceRecordsTest {

    private static final MediaRef REF =
            new MediaRef("https://example.com/a.mp4", MediaKind.VIDEO, "test:direct", Text.literal("[video]"));

    @Test
    void mediaRefRefusesWhatIsNotAWebLink() {
        assertThatThrownBy(() -> new MediaRef("file:///a.mp4", MediaKind.VIDEO, "test:x", Text.literal("x")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MediaRef("https://a.b/c", MediaKind.VIDEO, " ", Text.literal("x")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void streamUrlRefusesLocalFiles() {
        assertThat(StreamUrl.of("https://cdn.example/v").protocol()).isEqualTo("https");
        assertThatThrownBy(() -> StreamUrl.of("file:///dev/zero")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resolvedMediaNeedsAStreamAndPrefersThePicture() {
        var video = StreamUrl.of("https://cdn.example/v");
        var audio = StreamUrl.of("https://cdn.example/a");
        var both = new ResolvedMedia(
                REF, Optional.of(video), Optional.of(audio), Map.of(), MediaMetadata.UNKNOWN, Instant.EPOCH);
        var soundOnly = new ResolvedMedia(
                REF, Optional.empty(), Optional.of(audio), Map.of(), MediaMetadata.UNKNOWN, Instant.EPOCH);
        assertThat(both.primary()).isEqualTo(video);
        assertThat(soundOnly.primary()).isEqualTo(audio);
        assertThatThrownBy(() -> new ResolvedMedia(
                        REF, Optional.empty(), Optional.empty(), Map.of(), MediaMetadata.UNKNOWN, Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resolvedMediaRefusesHeadersThatWouldInjectLines() {
        assertThatThrownBy(() -> new ResolvedMedia(
                        REF,
                        Optional.of(StreamUrl.of("https://cdn.example/v")),
                        Optional.empty(),
                        Map.of("Referer", "https://a\r\nX-Evil: 1"),
                        MediaMetadata.UNKNOWN,
                        Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void metadataDropsMeaninglessDurationsAndRejectsNegativeGeometry() {
        var zero = new MediaMetadata(
                Optional.empty(), Optional.of(Duration.ZERO), Optional.empty(), false, 0, 0, 0, Tracks.UNKNOWN);
        assertThat(zero.duration()).isEmpty();
        assertThat(zero.withTracks(Tracks.AUDIO_ONLY).tracks()).isEqualTo(Tracks.AUDIO_ONLY);
        assertThatThrownBy(() -> new MediaMetadata(
                        Optional.empty(), Optional.empty(), Optional.empty(), false, -1, 0, 0, Tracks.UNKNOWN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MediaMetadata(
                        Optional.empty(), Optional.empty(), Optional.empty(), false, 0, 0, Double.NaN, Tracks.UNKNOWN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tracksSayWhatTheyContain() {
        assertThat(Tracks.of(true, true)).isEqualTo(Tracks.VIDEO_AND_AUDIO);
        assertThat(Tracks.of(true, false)).isEqualTo(Tracks.VIDEO_ONLY);
        assertThat(Tracks.of(false, true)).isEqualTo(Tracks.AUDIO_ONLY);
        assertThatThrownBy(() -> Tracks.of(false, false)).isInstanceOf(IllegalArgumentException.class);
        assertThat(Tracks.VIDEO_AND_AUDIO.hasVideo()).isTrue();
        assertThat(Tracks.VIDEO_AND_AUDIO.hasAudio()).isTrue();
        assertThat(Tracks.AUDIO_ONLY.hasVideo()).isFalse();
        assertThat(Tracks.UNKNOWN.hasAudio()).isFalse();
    }

    @Test
    void resolveOptionsRefuseAUselessHeight() {
        assertThat(ResolveOptions.DEFAULT.maxHeight()).isEqualTo(720);
        assertThatThrownBy(() -> new ResolveOptions(100, false)).isInstanceOf(IllegalArgumentException.class);
    }
}
