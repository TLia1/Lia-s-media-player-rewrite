package com.lia.mediaplayer.ui.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.source.LinkSource;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.link.BuiltinSources;
import com.lia.mediaplayer.core.link.SourceRegistry;
import com.lia.mediaplayer.core.text.Keys;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ShowCommandTest {

    record Played(String url, MediaKind kind, boolean audioOnly, boolean newWindow) {}

    private final List<Played> played = new ArrayList<>();

    /** The built-ins, plus a pretend image host: images arrive with chat integration. */
    private final ShowCommand command = new ShowCommand(
            new SourceRegistry(withImages()),
            (ref, audioOnly, newWindow) -> played.add(new Played(ref.url(), ref.kind(), audioOnly, newWindow)));

    private static List<LinkSource> withImages() {
        var sources = new ArrayList<>(BuiltinSources.all());
        sources.add(new LinkSource() {
            @Override
            public String id() {
                return "test:image";
            }

            @Override
            public Optional<MediaRef> match(URI uri, String raw) {
                return raw.endsWith(".png")
                        ? Optional.of(new MediaRef(raw, MediaKind.IMAGE, id(), Text.literal("[picture]")))
                        : Optional.empty();
            }
        });
        return sources;
    }

    @Test
    void playsAVideoInTheFrontWindowOrANewOne() {
        assertThat(command.run("video", "https://www.youtube.com/watch?v=abc")).isEmpty();
        assertThat(command.run("VIDEO", "https://example.com/a.mp4 true")).isEmpty();
        assertThat(command.run("video", "\"https://example.com/b.mp4\" false")).isEmpty();
        assertThat(played)
                .containsExactly(
                        new Played("https://www.youtube.com/watch?v=abc", MediaKind.VIDEO, false, false),
                        new Played("https://example.com/a.mp4", MediaKind.VIDEO, false, true),
                        new Played("https://example.com/b.mp4", MediaKind.VIDEO, false, false));
    }

    @Test
    void aVideoOrAPlaylistCanBeHeardWithoutItsPicture() {
        assertThat(command.run("audio", "https://youtu.be/abc", false)).isEmpty();
        assertThat(command.run("audio", "https://www.youtube.com/playlist?list=PL1", false))
                .isEmpty();
        assertThat(command.run("audio", "https://example.com/song.mp3", true)).isEmpty();
        assertThat(played).extracting(Played::audioOnly).containsOnly(true);
        assertThat(played)
                .extracting(Played::kind)
                .containsExactly(MediaKind.VIDEO, MediaKind.COLLECTION, MediaKind.AUDIO);
    }

    @Test
    void refusesWhatItCannotPlayAndSaysWhy() {
        assertThat(command.run("gif", "https://example.com/a.mp4", false))
                .contains(Text.translatable(Keys.COMMAND_INVALID_TYPE, "gif"));
        assertThat(command.run("video", "https://example.com/page", false))
                .contains(Text.translatable(Keys.COMMAND_UNSUPPORTED_URL));
        assertThat(command.run("video", "not a link", false)).contains(Text.translatable(Keys.COMMAND_UNSUPPORTED_URL));
        assertThat(command.run("video", "https://example.com/song.mp3", false))
                .contains(Text.translatable(Keys.COMMAND_KIND_MISMATCH, "audio", "video"));
        assertThat(command.run("image", "https://example.com/a.mp4", false))
                .contains(Text.translatable(Keys.COMMAND_KIND_MISMATCH, "video", "image"));
        assertThat(command.run("audio", "https://example.com/a.png", false))
                .contains(Text.translatable(Keys.COMMAND_KIND_MISMATCH, "image", "audio"));
        assertThat(command.run("image", "https://example.com/a.png", false))
                .contains(Text.translatable(Keys.COMMAND_IMAGES_LATER));
        assertThat(played).isEmpty();
        assertThat(ShowCommand.types()).containsExactly("video", "audio", "image");
    }
}
