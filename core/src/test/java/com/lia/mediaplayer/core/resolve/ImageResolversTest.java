package com.lia.mediaplayer.core.resolve;

import static org.assertj.core.api.Assertions.assertThat;

import com.lia.mediaplayer.api.source.ResolveOptions;
import com.lia.mediaplayer.api.source.ResolvedMedia;
import com.lia.mediaplayer.core.link.BuiltinSources;
import com.lia.mediaplayer.core.link.SourceRegistry;
import java.time.Instant;
import java.time.InstantSource;
import org.junit.jupiter.api.Test;

class ImageResolversTest {

    private final SourceRegistry sources = new SourceRegistry(BuiltinSources.all());
    private final InstantSource clock = InstantSource.fixed(Instant.parse("2026-10-29T12:00:00Z"));

    @Test
    void aGiphyPageResolvesToItsGifWithoutARequest() throws Exception {
        var resolver = new GiphyResolver(clock);
        var page = sources.match("https://giphy.com/gifs/happy-dance-l0MYt5jPR6QX5pnqM")
                .orElseThrow();
        assertThat(resolver.handles(page)).isTrue();
        assertThat(resolver.handles(
                        sources.match("https://tenor.com/view/x-gif-1").orElseThrow()))
                .isFalse();

        ResolvedMedia media = resolver.resolve(page, ResolveOptions.DEFAULT).get();
        assertThat(media.primary().url()).isEqualTo("https://i.giphy.com/media/l0MYt5jPR6QX5pnqM/giphy.gif");
        assertThat(media.expiresAt()).isAfter(clock.instant());
    }

    @Test
    void aDirectPictureNeedsNoResolution() throws Exception {
        var page = sources.match("https://cdn.example/a.gif").orElseThrow();
        var resolver = new DirectResolver(clock);
        assertThat(resolver.handles(page)).isTrue();
        assertThat(resolver.resolve(page, ResolveOptions.DEFAULT)
                        .get()
                        .primary()
                        .url())
                .isEqualTo("https://cdn.example/a.gif");
    }

    @Test
    void findsTheGifOnATenorPage() {
        assertThat(TenorResolver.gifOf(
                        "<div><img src=\"https://media1.tenor.com/m/AbCdEfGhIjKAAAAd/cat-dance.gif\" alt=\"x\"></div>"))
                .contains("https://c.tenor.com/AbCdEfGhIjKAAAAd/tenor.gif");
        assertThat(TenorResolver.gifOf(
                        "<meta itemprop=\"contentUrl\" content=\"https://media.tenor.com/m/ZyXwVuTsRqP/cat.mp4\">"))
                .contains("https://c.tenor.com/ZyXwVuTsRqP/tenor.gif");
        assertThat(TenorResolver.gifOf(
                        "<meta content=\"https://media.tenor.com/m/ZyXwVuTsRqP/cat.mp4\" itemprop=\"contentUrl\">"))
                .contains("https://c.tenor.com/ZyXwVuTsRqP/tenor.gif");
        assertThat(TenorResolver.gifOf("<meta property=\"og:image\" content=\"https://c.tenor.com/old/a.gif\">"))
                .contains("https://c.tenor.com/old/a.gif");
        assertThat(TenorResolver.gifOf("<meta property=\"og:image\" content=\"file:///etc/x.gif\">"))
                .as("only a web address goes further")
                .isEmpty();
        assertThat(TenorResolver.gifOf("<html>nothing</html>")).isEmpty();
    }
}
