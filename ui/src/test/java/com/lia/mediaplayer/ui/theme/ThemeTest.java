package com.lia.mediaplayer.ui.theme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lia.mediaplayer.api.text.Text;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ThemeTest {

    @Test
    void theDarkThemeSetsEveryRole() {
        assertThat(Themes.DARK.colors()).containsOnlyKeys(Role.values());
        for (Role role : Role.values()) {
            assertThat(Themes.DARK.color(role)).isEqualTo(Themes.DARK.colors().get(role));
        }
        assertThat(Themes.DARK.id()).isEqualTo("liasmediaplayer:dark");
        assertThat(Themes.DARK.name()).isEqualTo(Text.translatable("theme.liasmediaplayer.dark"));
        assertThat(Themes.DARK.toString()).contains("dark");
    }

    @Test
    void aThemeIsAnotherPlusWhatItChanges() {
        Theme red = Themes.DARK.with("test:red", Text.literal("Red"), Map.of(Role.FILL, 0xFFFF0000));
        assertThat(red.color(Role.FILL)).isEqualTo(0xFFFF0000);
        assertThat(red.color(Role.TEXT)).isEqualTo(Themes.DARK.color(Role.TEXT));
        assertThat(Themes.DARK.color(Role.FILL)).isNotEqualTo(0xFFFF0000);
    }

    @Test
    void aThemeMissingARoleIsRefused() {
        assertThatThrownBy(() -> Theme.of("test:bare", Text.literal("Bare"), Map.of(Role.TEXT, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("test:bare");
    }
}
