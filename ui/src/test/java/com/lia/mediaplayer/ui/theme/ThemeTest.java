package com.lia.mediaplayer.ui.theme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.api.ui.ThemeRole;
import com.lia.mediaplayer.api.ui.ThemeSpec;
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
    void everyBuiltInThemeHasItsOwnIdAndNameAndEveryRole() {
        assertThat(Themes.all()).extracting(Theme::id).doesNotHaveDuplicates();
        for (Theme theme : Themes.all()) {
            assertThat(theme.colors()).containsOnlyKeys(Role.values());
            assertThat(theme.name()).isInstanceOf(Text.Translatable.class);
        }
        assertThat(Themes.LIGHT.color(Role.TEXT)).isNotEqualTo(Themes.DARK.color(Role.TEXT));
    }

    @Test
    void theRegistryFallsBackToDarkAndRefusesTwins() {
        var registry = new ThemeRegistry();
        assertThat(registry.byId("liasmediaplayer:light")).isSameAs(Themes.LIGHT);
        assertThat(registry.byId("someaddon:gone")).isSameAs(Themes.DARK);
        Theme red = Themes.DARK.with("test:red", Text.literal("Red"), Map.of(Role.FILL, 0xFFFF0000));
        registry.register(red);
        assertThat(registry.all()).endsWith(red);
        assertThat(registry.choices()).extracting(c -> c.id()).contains("test:red", "liasmediaplayer:dark");
        assertThatThrownBy(() -> registry.register(red)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aThemeMissingARoleIsRefused() {
        assertThatThrownBy(() -> Theme.of("test:bare", Text.literal("Bare"), Map.of(Role.TEXT, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("test:bare");
    }

    @Test
    void everyRoleAnAddonCanNameIsARoleOfTheUi() {
        for (ThemeRole role : ThemeRole.values()) {
            assertThat(Role.of(role).name()).isEqualTo(role.name());
        }
        assertThat(ThemeRole.values()).hasSameSizeAs(Role.values());
        Theme sunset = Themes.from(
                ThemeSpec.of("myaddon:sunset", Text.literal("Sunset")).with(ThemeRole.FILL, 0xFFFF8A50));
        assertThat(sunset.color(Role.FILL)).isEqualTo(0xFFFF8A50);
        assertThat(sunset.color(Role.TEXT)).isEqualTo(Themes.DARK.color(Role.TEXT));
    }
}
