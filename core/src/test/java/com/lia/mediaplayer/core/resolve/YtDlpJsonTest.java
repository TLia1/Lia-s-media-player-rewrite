package com.lia.mediaplayer.core.resolve;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.source.MediaMetadata.Tracks;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.api.source.ResolvedMedia;
import com.lia.mediaplayer.api.text.Text;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class YtDlpJsonTest {

    private static final MediaRef REF = new MediaRef(
            "https://www.youtube.com/watch?v=abc", MediaKind.VIDEO, "liasmediaplayer:youtube", Text.literal("[yt]"));
    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");

    @Test
    void readsSeparatePictureAndSound() throws IOException {
        ResolvedMedia media = YtDlpJson.parse("""
                {"title": "Clip", "duration": 212.5, "thumbnail": "https://i.ytimg.com/vi/abc/hq.jpg",
                 "is_live": false, "width": 1280, "height": 720, "fps": 30,
                 "http_headers": {"User-Agent": "UA", "Accept": "*/*"},
                 "requested_formats": [
                   {"url": "https://rr1.googlevideo.com/videoplayback?expire=1793275200&itag=136",
                    "protocol": "https", "vcodec": "avc1.4d401f", "acodec": "none", "width": 1280, "height": 720, "fps": 25,
                    "http_headers": {"User-Agent": "UA", "Referer": "https://www.youtube.com/"}},
                   {"url": "https://rr1.googlevideo.com/videoplayback?expire=1793271600&itag=140",
                    "protocol": "https", "vcodec": "none", "acodec": "mp4a.40.2"}]}
                """, REF, NOW);

        assertThat(media.video()).hasValueSatisfying(v -> assertThat(v.url()).contains("itag=136"));
        assertThat(media.audio()).hasValueSatisfying(a -> assertThat(a.url()).contains("itag=140"));
        assertThat(media.httpHeaders())
                .containsEntry("User-Agent", "UA")
                .containsEntry("Referer", "https://www.youtube.com/")
                .containsEntry("Accept", "*/*");
        assertThat(media.metadata().title()).contains("Clip");
        assertThat(media.metadata().duration()).contains(Duration.ofMillis(212_500));
        assertThat(media.metadata().thumbnail()).contains("https://i.ytimg.com/vi/abc/hq.jpg");
        assertThat(media.metadata().tracks()).isEqualTo(Tracks.VIDEO_AND_AUDIO);
        assertThat(media.metadata().fps()).isEqualTo(25.0);
        // The earlier of the two deadlines, minus the margin.
        assertThat(media.expiresAt())
                .isEqualTo(Instant.ofEpochSecond(1793271600).minus(StreamExpiry.MARGIN));
    }

    @Test
    void readsASingleMuxedStream() throws IOException {
        ResolvedMedia media = YtDlpJson.parse("""
                {"url": "https://cdn.example/v.m3u8", "protocol": "m3u8_native", "vcodec": "avc1", "acodec": "mp4a",
                 "is_live": true, "title": "Live"}
                """, REF, NOW);
        assertThat(media.video())
                .hasValueSatisfying(v -> assertThat(v.protocol()).isEqualTo("m3u8_native"));
        assertThat(media.audio()).isEmpty();
        assertThat(media.metadata().tracks()).isEqualTo(Tracks.VIDEO_AND_AUDIO);
        assertThat(media.metadata().live()).isTrue();
        assertThat(media.expiresAt()).isEqualTo(NOW.plus(StreamExpiry.DEFAULT_VALIDITY));
    }

    @Test
    void readsSoundOnlyAndUnknownCodecs() throws IOException {
        ResolvedMedia sound = YtDlpJson.parse("""
                {"url": "https://cf.sndcdn.com/a.mp3", "vcodec": "none", "acodec": "mp3"}
                """, REF, NOW);
        assertThat(sound.audio()).isPresent();
        assertThat(sound.video()).isEmpty();
        assertThat(sound.metadata().tracks()).isEqualTo(Tracks.AUDIO_ONLY);

        ResolvedMedia unknown = YtDlpJson.parse("""
                {"url": "https://cdn.example/file"}
                """, REF, NOW);
        assertThat(unknown.video()).isPresent();
        assertThat(unknown.metadata().tracks()).isEqualTo(Tracks.UNKNOWN);
    }

    @Test
    void dropsHeadersThatCouldInjectLinesAndThumbnailsThatAreNotWebLinks() throws IOException {
        ResolvedMedia media = YtDlpJson.parse("""
                {"url": "https://cdn.example/v.mp4", "thumbnail": "file:///etc/passwd",
                 "http_headers": {"X-Evil": "a\\r\\nX-Injected: 1", "Cookie": "ok"}}
                """, REF, NOW);
        assertThat(media.httpHeaders()).containsOnlyKeys("Cookie");
        assertThat(media.metadata().thumbnail()).isEmpty();
    }

    @Test
    void refusesPlaylistsLocalStreamsAndGarbage() {
        assertThatThrownBy(() -> YtDlpJson.parse("{\"_type\": \"playlist\", \"entries\": []}", REF, NOW))
                .hasMessageContaining("playlist");
        assertThatThrownBy(() -> YtDlpJson.parse("{\"url\": \"file:///dev/zero\"}", REF, NOW))
                .hasMessageContaining("not http(s)");
        assertThatThrownBy(() -> YtDlpJson.parse("{\"title\": \"no url\"}", REF, NOW))
                .hasMessageContaining("no stream URL");
        assertThatThrownBy(() -> YtDlpJson.parse("{\"requested_formats\": [{\"url\": \"https://a.b/c\"}]}", REF, NOW))
                .hasMessageContaining("no playable format");
        assertThatThrownBy(() -> YtDlpJson.parse("[1, 2]", REF, NOW)).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> YtDlpJson.parse("{not json", REF, NOW)).isInstanceOf(IOException.class);
    }

    @Test
    void findsTheDeadlineInTheQueryOrThePath() {
        assertThat(StreamExpiry.of("https://a.googlevideo.com/videoplayback?itag=1&expire=1793275200&x=y"))
                .contains(Instant.ofEpochSecond(1793275200));
        assertThat(StreamExpiry.of("https://manifest.googlevideo.com/api/manifest/hls/expire/1793275200/id/x"))
                .contains(Instant.ofEpochSecond(1793275200));
        assertThat(StreamExpiry.of("https://cdn.example/v.mp4?notexpire=1793275200"))
                .isEmpty();
        assertThat(StreamExpiry.of("https://cdn.example/v.mp4")).isEmpty();
        assertThat(StreamExpiry.of("not a uri at all ::")).isEmpty();
    }
}
