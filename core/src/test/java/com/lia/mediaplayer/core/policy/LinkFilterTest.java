package com.lia.mediaplayer.core.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class LinkFilterTest {

    @Test
    void aHostCoversItsSubDomainsAndOnlyThose() {
        List<String> hosts = List.of("discordapp.com");
        assertThat(LinkFilter.hostMatches("https://discordapp.com/a.png", hosts))
                .isTrue();
        assertThat(LinkFilter.hostMatches("https://cdn.discordapp.com/a.png", hosts))
                .isTrue();
        assertThat(LinkFilter.hostMatches("https://www.discordapp.com/a.png", hosts))
                .isTrue();
        assertThat(LinkFilter.hostMatches("https://evil-discordapp.com/a.png", hosts))
                .isFalse();
        assertThat(LinkFilter.hostMatches("https://discordapp.com.evil/a.png", hosts))
                .isFalse();
        assertThat(LinkFilter.hostMatches("not a link", hosts)).isFalse();
        assertThat(LinkFilter.hostMatches("https://a.example/", List.of())).isFalse();
    }

    @Test
    void entriesAreReadTheWayTheyAreWritten() {
        var filter = new LinkFilter(LinkFilter.Mode.ALLOWLIST, List.of(), List.of("  WWW.Imgur.com ", ""), List.of());
        assertThat(filter.allowedHosts()).containsExactly("www.imgur.com");
        assertThat(filter.allowsUrl("https://i.imgur.com/a.png")).isTrue();
        assertThat(filter.allowsUrl("https://imgur.com.evil/a.png")).isFalse();
    }

    @Test
    void theModeDecidesWhichListApplies() {
        var blocked = List.of("bad.example");
        assertThat(new LinkFilter(LinkFilter.Mode.OFF, blocked, List.of(), List.of()).allowsUrl("https://bad.example"))
                .isTrue();
        assertThat(new LinkFilter(LinkFilter.Mode.BLOCKLIST, blocked, List.of(), List.of())
                        .allowsUrl("https://bad.example"))
                .isFalse();
        assertThat(new LinkFilter(LinkFilter.Mode.BLOCKLIST, blocked, List.of(), List.of())
                        .allowsUrl("https://good.example"))
                .isTrue();
        assertThat(new LinkFilter(LinkFilter.Mode.ALLOWLIST, blocked, List.of(), List.of())
                        .allowsUrl("https://good.example"))
                .as("an empty allow-list allows nothing")
                .isFalse();
    }

    @Test
    void aBlockedNameIsFoundInsideADisplayName() {
        var filter = new LinkFilter(LinkFilter.Mode.OFF, List.of(), List.of(), List.of("Steve"));
        assertThat(filter.allowsSender("[VIP] Steve")).isFalse();
        assertThat(filter.allowsSender("steve_")).isFalse();
        assertThat(filter.allowsSender("Alex")).isTrue();
        assertThat(filter.allowsSender(null)).as("a system message").isTrue();
        assertThat(LinkFilter.NONE.allowsSender("Steve")).isTrue();
    }
}
