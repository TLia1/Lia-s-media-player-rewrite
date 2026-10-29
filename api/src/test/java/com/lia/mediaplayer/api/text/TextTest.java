package com.lia.mediaplayer.api.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lia.mediaplayer.api.media.RepeatMode;
import java.util.List;
import org.junit.jupiter.api.Test;

class TextTest {

    @Test
    void buildsLiteralsAndTranslations() {
        assertThat(Text.literal("hi")).isEqualTo(new Text.Literal("hi"));
        var text = Text.translatable("a.b", "x", 3, Text.literal("y"));
        assertThat(text).isEqualTo(new Text.Translatable("a.b", List.of("x", 3, Text.literal("y"))));
    }

    @Test
    void refusesBlankKeysAndArgumentsItCannotShow() {
        assertThatThrownBy(() -> Text.translatable(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Text.translatable("a.b", new Object())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void repeatModesCycleLikeTheLoopButton() {
        assertThat(RepeatMode.OFF.next()).isEqualTo(RepeatMode.ALL);
        assertThat(RepeatMode.ALL.next()).isEqualTo(RepeatMode.ONE);
        assertThat(RepeatMode.ONE.next()).isEqualTo(RepeatMode.OFF);
    }
}
