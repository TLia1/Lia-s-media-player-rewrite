package com.lia.mediaplayer.core.link;

import static org.assertj.core.api.Assertions.assertThat;

import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.policy.LinkFilter;
import com.lia.mediaplayer.core.text.Keys;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatRewriterTest {

    private final ChatRewriter rewriter = new ChatRewriter(new SourceRegistry(BuiltinSources.all()));

    @Test
    void labelsEveryRecognisedLinkInItsRun() {
        List<ChatLink> links = rewriter.rewrite(
                List.of(
                        "<Steve> ",
                        "look https://a.example/cat.png and https://youtu.be/abc",
                        " bye https://x.example/page"),
                "Steve");

        assertThat(links).hasSize(2);
        ChatLink picture = links.getFirst();
        assertThat(picture.run()).isEqualTo(1);
        assertThat(picture.start()).isEqualTo(5);
        assertThat(picture.end()).isEqualTo(30);
        assertThat(picture.ref().kind()).isEqualTo(MediaKind.IMAGE);
        assertThat(picture.label()).isEqualTo(Text.translatable("chat.liasmediaplayer.label.picture"));
        assertThat(picture.hint()).as("a picture's hover is its preview").isEmpty();
        assertThat(picture.underlined()).isFalse();

        ChatLink video = links.get(1);
        assertThat(video.url()).isEqualTo("https://youtu.be/abc");
        assertThat(video.hint()).contains(Text.translatable(Keys.CHAT_HINT_VIDEO));
        assertThat(video.underlined()).isTrue();
        assertThat(video.rgb()).isNotEqualTo(picture.rgb());

        assertThat(rewriter.isLabelled("https://youtu.be/abc")).isTrue();
        assertThat(rewriter.isLabelled("https://x.example/page")).isFalse();
    }

    @Test
    void aSetOfSoundsLooksLikeSound() {
        ChatLink set = rewriter.rewrite(List.of("https://soundcloud.com/a/sets/b"), null)
                .getFirst();
        ChatLink song =
                rewriter.rewrite(List.of("https://soundcloud.com/a/b"), null).getFirst();
        assertThat(set.rgb()).isEqualTo(song.rgb());
        assertThat(set.hint()).contains(Text.translatable(Keys.CHAT_HINT_AUDIO_COLLECTION));
        assertThat(song.hint()).contains(Text.translatable(Keys.CHAT_HINT_AUDIO));
        assertThat(rewriter.rewrite(List.of("https://youtube.com/playlist?list=PL1"), null)
                        .getFirst()
                        .hint())
                .contains(Text.translatable(Keys.CHAT_HINT_COLLECTION));
    }

    @Test
    void aMessageWithoutMediaIsLeftAlone() {
        assertThat(rewriter.rewrite(List.of("no links here"), null)).isEmpty();
        assertThat(rewriter.rewrite(List.of("https://x.example/page.html"), null))
                .isEmpty();
        assertThat(rewriter.rewrite(List.of(), null)).isEmpty();
    }

    @Test
    void followsThePlayersFilter() {
        rewriter.setFilter(
                new LinkFilter(LinkFilter.Mode.BLOCKLIST, List.of("bad.example"), List.of(), List.of("Griefer")));

        assertThat(rewriter.rewrite(List.of("https://good.example/a.png"), "[VIP] griefer42"))
                .as("a blocked sender's links stay text")
                .isEmpty();
        List<ChatLink> links =
                rewriter.rewrite(List.of("https://cdn.bad.example/a.png https://good.example/b.png"), "Steve");
        assertThat(links).extracting(ChatLink::url).containsExactly("https://good.example/b.png");
        assertThat(rewriter.isLabelled("https://cdn.bad.example/a.png")).isFalse();
        assertThat(rewriter.filter().mode()).isEqualTo(LinkFilter.Mode.BLOCKLIST);
    }
}
