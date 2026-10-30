package com.lia.mediaplayer.api.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lia.mediaplayer.api.text.Text;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UiSpecsTest {

    @Test
    void aThemeIsTheRolesItChanges() {
        ThemeSpec sunset = ThemeSpec.of("myaddon:sunset", Text.literal("Sunset"))
                .with(ThemeRole.FILL, 0xFFFF8A50)
                .with(ThemeRole.WINDOW_BG, 0xD0221018)
                .with(ThemeRole.FILL, 0xFFFF0000);
        assertThat(sunset.colors())
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(ThemeRole.FILL, 0xFFFF0000, ThemeRole.WINDOW_BG, 0xD0221018));
        assertThatThrownBy(() -> ThemeSpec.of("sunset", Text.literal("x")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aShortcutIsALetterOrADigit() {
        var shortcut = ShortcutSpec.ctrl("myaddon:share", 'j', player -> false);
        assertThat(shortcut.key()).isEqualTo('J');
        assertThat(shortcut.shift()).isFalse();
        assertThat(new ShortcutSpec("myaddon:seven", '7', true, player -> true).key())
                .isEqualTo('7');
        assertThatThrownBy(() -> ShortcutSpec.ctrl("myaddon:space", ' ', player -> false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ShortcutSpec.ctrl("share", 'J', player -> false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(shortcut.action().test(Optional.empty())).isFalse();
    }

    @Test
    void aKeyBindingHasANameKey() {
        var binding = new KeyBindingSpec("myaddon:share", "key.myaddon.share", player -> {});
        assertThat(binding.nameKey()).isEqualTo("key.myaddon.share");
        assertThatThrownBy(() -> new KeyBindingSpec("myaddon:share", " ", player -> {}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KeyBindingSpec("share", "key.x", player -> {}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aWindowActionShowsEverywhereByDefault() {
        WindowAction action = new WindowAction() {
            @Override
            public String id() {
                return "myaddon:share";
            }

            @Override
            public ActionIcon icon() {
                return ActionIcon.EXTERNAL_LINK;
            }

            @Override
            public Text tooltip() {
                return Text.literal("Share");
            }

            @Override
            public void onClick(MediaWindow window, boolean shift) {}
        };
        assertThat(action.appliesTo(null)).isTrue();
    }
}
