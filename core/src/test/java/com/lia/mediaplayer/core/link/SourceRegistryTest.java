package com.lia.mediaplayer.core.link;

import static org.assertj.core.api.Assertions.assertThat;

import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.source.LinkSource;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.api.text.Text;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SourceRegistryTest {

    private final SourceRegistry registry = new SourceRegistry(BuiltinSources.all());

    @ParameterizedTest
    @CsvSource({
        "https://www.youtube.com/watch?v=dQw4w9WgXcQ, liasmediaplayer:youtube, VIDEO",
        "https://youtu.be/dQw4w9WgXcQ, liasmediaplayer:youtube, VIDEO",
        "https://m.youtube.com/shorts/abc, liasmediaplayer:youtube, VIDEO",
        "https://music.youtube.com/watch?v=abc&list=RD, liasmediaplayer:youtube, VIDEO",
        "https://youtube.com/live/abc, liasmediaplayer:youtube, VIDEO",
        "https://www.youtube.com/embed/abc, liasmediaplayer:youtube, VIDEO",
        "https://www.youtube.com/playlist?list=PL123, liasmediaplayer:youtube_playlist, COLLECTION",
        "https://soundcloud.com/artist/track, liasmediaplayer:soundcloud, AUDIO",
        "https://snd.sc/abc, liasmediaplayer:soundcloud, AUDIO",
        "https://cdn.example/clip.MP4?x=1, liasmediaplayer:video_file, VIDEO",
        "https://cdn.example/a/b.webm, liasmediaplayer:video_file, VIDEO",
        "https://cdn.example/song.flac, liasmediaplayer:audio_file, AUDIO",
        "https://cdn.example/live/index.m3u8, liasmediaplayer:stream, VIDEO",
        "https://cdn.example/dash.mpd, liasmediaplayer:stream, VIDEO",
        "https://cdn.discordapp.com/attachments/1/2/cat.PNG, liasmediaplayer:picture, IMAGE",
        "https://i.imgur.com/abc.jpeg?x=1, liasmediaplayer:picture, IMAGE",
        "https://media.tenor.com/abc/dance.gif, liasmediaplayer:picture, IMAGE",
        "https://tenor.com/view/cat-dance-gif-12345, liasmediaplayer:tenor, IMAGE",
        "https://tenor.com/fr/view/cat-gif-12345, liasmediaplayer:tenor, IMAGE",
        "https://giphy.com/gifs/funny-cat-JIX9t2j0ZTN9S, liasmediaplayer:giphy, IMAGE",
        "https://www.twitch.tv/somestreamer, liasmediaplayer:twitch, VIDEO",
        "https://m.twitch.tv/videos/123, liasmediaplayer:twitch, VIDEO",
        "https://vimeo.com/76979871, liasmediaplayer:vimeo, VIDEO",
        "https://vimeo.com/76979871/abcdef, liasmediaplayer:vimeo, VIDEO",
        "https://player.vimeo.com/video/76979871, liasmediaplayer:vimeo, VIDEO",
        "https://streamable.com/abc12, liasmediaplayer:streamable, VIDEO",
        "https://streamable.com/e/abc12, liasmediaplayer:streamable, VIDEO",
        "https://v.redd.it/abc123, liasmediaplayer:reddit, VIDEO",
        "https://www.reddit.com/r/videos/comments/abc/title/, liasmediaplayer:reddit, VIDEO",
        "https://artist.bandcamp.com/track/song, liasmediaplayer:bandcamp, AUDIO",
        "https://artist.bandcamp.com/album/record, liasmediaplayer:bandcamp_album, COLLECTION",
        "https://soundcloud.com/artist/sets/mix, liasmediaplayer:soundcloud_set, COLLECTION"
    })
    void recognisesTheBuiltInLinks(String url, String source, MediaKind kind) {
        MediaRef ref = registry.match(url).orElseThrow();
        assertThat(ref.sourceId()).isEqualTo(source);
        assertThat(ref.kind()).isEqualTo(kind);
        assertThat(ref.url()).isEqualTo(url);
        assertThat(ref.label()).isInstanceOf(Text.Translatable.class);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://www.youtube.com/",
                "https://www.youtube.com/@channel",
                "https://www.youtube.com/playlist",
                "https://youtu.be/",
                "https://soundcloud.com/",
                "https://notyoutube.com/watch?v=abc",
                "https://cdn.example/page.html",
                "https://cdn.example/mp4",
                "https://cdn.example/a.webp",
                "https://tenor.com/search/cats",
                "https://giphy.com/gifs/cats",
                "https://giphy.com/explore/cats",
                "https://www.twitch.tv/",
                "https://vimeo.com/channels/staffpicks",
                "https://player.vimeo.com/video/",
                "https://streamable.com/",
                "https://streamable.com/averyverylongname",
                "https://www.reddit.com/r/videos/",
                "https://artist.bandcamp.com/",
                "https://notbandcamp.com/track/x",
                "file:///home/me/a.mp4",
                "javascript:alert(1)",
                "-https://cdn.example/a.mp4"
            })
    void leavesEverythingElseAlone(String url) {
        assertThat(registry.match(url)).isEmpty();
    }

    @Test
    void asksHigherPrioritySourcesFirstAndSkipsOnesThatThrow() {
        LinkSource broken = new LinkSource() {
            @Override
            public String id() {
                return "test:broken";
            }

            @Override
            public Optional<MediaRef> match(URI uri, String raw) {
                throw new IllegalStateException("bug");
            }

            @Override
            public int priority() {
                return 100;
            }
        };
        LinkSource everything = new LinkSource() {
            @Override
            public String id() {
                return "test:everything";
            }

            @Override
            public Optional<MediaRef> match(URI uri, String raw) {
                return Optional.of(new MediaRef(raw, MediaKind.VIDEO, id(), Text.literal("x")));
            }

            @Override
            public int priority() {
                return 50;
            }
        };
        var withAddons = new SourceRegistry(List.of(BuiltinSources.all().getFirst(), everything, broken));
        assertThat(withAddons.sources().getFirst()).isSameAs(broken);
        assertThat(withAddons.match("https://www.youtube.com/playlist?list=PL1"))
                .hasValueSatisfying(ref -> assertThat(ref.sourceId()).isEqualTo("test:everything"));
    }

    @Test
    void knowsWhichSourcesAreDirect() {
        assertThat(BuiltinSources.isDirect(BuiltinSources.VIDEO_FILE)).isTrue();
        assertThat(BuiltinSources.isDirect(BuiltinSources.AUDIO_FILE)).isTrue();
        assertThat(BuiltinSources.isDirect(BuiltinSources.STREAM)).isTrue();
        assertThat(BuiltinSources.isDirect(BuiltinSources.YOUTUBE)).isFalse();
    }

    @Test
    void aListPlaysAsWhatItLists() {
        assertThat(registry.playsAs(
                        registry.match("https://soundcloud.com/a/sets/b").orElseThrow()))
                .isEqualTo(MediaKind.AUDIO);
        assertThat(registry.playsAs(
                        registry.match("https://a.bandcamp.com/album/b").orElseThrow()))
                .isEqualTo(MediaKind.AUDIO);
        assertThat(registry.playsAs(
                        registry.match("https://youtube.com/playlist?list=PL1").orElseThrow()))
                .isEqualTo(MediaKind.VIDEO);
        assertThat(registry.playsAs(registry.match("https://a.example/x.png").orElseThrow()))
                .isEqualTo(MediaKind.IMAGE);
        var unknown = new MediaRef("https://a.example/list", MediaKind.COLLECTION, "test:list", Text.literal("x"));
        assertThat(registry.playsAs(unknown)).isEqualTo(MediaKind.VIDEO);
    }

    @Test
    void giphyPagesBecomeTheirGif() {
        assertThat(BuiltinSources.giphyGif(URI.create("https://giphy.com/gifs/funny-cat-JIX9t2j0ZTN9S")))
                .contains("https://i.giphy.com/media/JIX9t2j0ZTN9S/giphy.gif");
        assertThat(BuiltinSources.giphyGif(URI.create("https://giphy.com/stickers/JIX9t2j0ZTN9S/extra")))
                .contains("https://i.giphy.com/media/JIX9t2j0ZTN9S/giphy.gif");
        assertThat(BuiltinSources.giphyGif(URI.create("https://giphy.com/gifs/bad-id-1234567!")))
                .isEmpty();
        assertThat(BuiltinSources.labelKeys()).contains("chat.liasmediaplayer.label.gif");
    }
}
