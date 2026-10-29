package com.lia.mediaplayer.core.link;

import static org.assertj.core.api.Assertions.assertThat;

import com.lia.mediaplayer.api.source.MediaRef;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ShareLinksTest {

    private final SourceRegistry sources = new SourceRegistry(BuiltinSources.all());

    private String at(String url, long seconds) {
        MediaRef ref = sources.match(url).orElseThrow();
        return ShareLinks.atTime(ref, Duration.ofSeconds(seconds)).orElse("none");
    }

    @Test
    void eachSiteSpellsThePositionItsOwnWay() {
        assertThat(at("https://www.youtube.com/watch?v=abc&t=5s#x", 137))
                .isEqualTo("https://www.youtube.com/watch?v=abc&t=137s#x");
        assertThat(at("https://youtu.be/abc", 61)).isEqualTo("https://youtu.be/abc?t=61s");
        assertThat(at("https://www.youtube.com/embed/abc", 61)).isEqualTo("https://www.youtube.com/embed/abc?start=61");
        assertThat(at("https://www.twitch.tv/videos/123", 3723))
                .isEqualTo("https://www.twitch.tv/videos/123?t=1h02m03s");
        assertThat(at("https://vimeo.com/123", 90)).isEqualTo("https://vimeo.com/123#t=90s");
        assertThat(at("https://soundcloud.com/artist/track", 3723))
                .isEqualTo("https://soundcloud.com/artist/track#t=1:02:03");
    }

    @Test
    void notAtTheStartNorWhereTheSiteCannotSayIt() {
        assertThat(at("https://www.youtube.com/watch?v=abc", 0)).isEqualTo("none");
        assertThat(at("https://example.com/clip.mp4", 30)).isEqualTo("none");
    }

    @Test
    void timesAreSpelledShort() {
        assertThat(ShareLinks.twitchTime(5)).isEqualTo("5s");
        assertThat(ShareLinks.twitchTime(65)).isEqualTo("1m05s");
        assertThat(ShareLinks.clockTime(65)).isEqualTo("1:05");
    }
}
