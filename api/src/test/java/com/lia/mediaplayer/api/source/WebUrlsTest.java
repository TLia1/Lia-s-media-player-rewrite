package com.lia.mediaplayer.api.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WebUrlsTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://example.com/a.mp4",
                "http://example.com",
                "HTTPS://EXAMPLE.COM/x",
                "https://my_host.example/clip.webm",
                "https://example.com:8443/a?b=c#d"
            })
    void acceptsWebLinks(String url) {
        assertThat(WebUrls.isWebUrl(url)).isTrue();
        assertThat(WebUrls.require(url)).isEqualTo(url);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "   ",
                "file:///etc/passwd",
                "https:///x.mp4",
                "concat:a.mp4|b.mp4",
                "-i",
                "--exec=rm",
                "ftp://example.com/a.mp4",
                "example.com/a.mp4",
                "https://exa mple.com"
            })
    void rejectsEverythingElse(String url) {
        assertThat(WebUrls.isWebUrl(url)).isFalse();
        assertThatThrownBy(() -> WebUrls.require(url)).isInstanceOf(IllegalArgumentException.class);
    }
}
