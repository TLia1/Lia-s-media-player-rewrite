package com.lia.mediaplayer.core.link;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LinkScannerTest {

    @Test
    void findsEveryLinkWithItsPosition() {
        String text = "look https://a.example/x.png and http://b.example/y.mp4!";
        assertThat(LinkScanner.scan(text))
                .containsExactly(
                        new LinkScanner.Span(5, 28, "https://a.example/x.png"),
                        new LinkScanner.Span(33, 55, "http://b.example/y.mp4"));
    }

    @Test
    void aMessageWithoutALinkCostsNothing() {
        assertThat(LinkScanner.mayContainLink("hello there")).isFalse();
        assertThat(LinkScanner.scan("hello there")).isEmpty();
        assertThat(LinkScanner.scan("http is a protocol")).isEmpty();
        assertThat(LinkScanner.scan("HTTPS://A.EXAMPLE/X.PNG"))
                .extracting(LinkScanner.Span::url)
                .containsExactly("HTTPS://A.EXAMPLE/X.PNG");
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "see https://a.example/x.png.|https://a.example/x.png",
                "(https://a.example/x.png)|https://a.example/x.png",
                "\"https://a.example/x.png\"|https://a.example/x.png",
                "https://en.wikipedia.org/wiki/Foo_(bar)|https://en.wikipedia.org/wiki/Foo_(bar)",
                "(see https://en.wikipedia.org/wiki/Foo_(bar))|https://en.wikipedia.org/wiki/Foo_(bar)",
                "https://a.example/?q=1&r=2;|https://a.example/?q=1&r=2"
            })
    void leavesOutTheSentencesPunctuation(String text, String url) {
        assertThat(LinkScanner.scan(text)).extracting(LinkScanner.Span::url).isEqualTo(List.of(url));
    }
}
