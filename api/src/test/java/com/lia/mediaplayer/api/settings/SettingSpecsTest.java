package com.lia.mediaplayer.api.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lia.mediaplayer.api.media.RepeatMode;
import com.lia.mediaplayer.api.text.Text;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SettingSpecsTest {

    private static final SettingInfo INFO = SettingInfo.of("myaddon:volume", "myaddon:sound", Text.literal("Volume"));

    @Test
    void idsAndGroupsAreNamespaced() {
        assertThatThrownBy(() -> SettingInfo.of("volume", "myaddon:sound", Text.literal("x")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SettingInfo.of("myaddon:volume", "Sound", Text.literal("x")))
                .isInstanceOf(IllegalArgumentException.class);
        var described = INFO.withDescription(Text.literal("How loud")).withWarning(Text.literal("Careful"));
        var spec = new BoolSetting(described, true);
        assertThat(spec.id()).isEqualTo("myaddon:volume");
        assertThat(spec.group()).isEqualTo("myaddon:sound");
        assertThat(spec.label()).isEqualTo(Text.literal("Volume"));
        assertThat(spec.description()).contains(Text.literal("How loud"));
        assertThat(spec.warning()).contains(Text.literal("Careful"));
        assertThat(spec.normalize(false)).isFalse();
    }

    @Test
    void numbersAreClampedToTheirRange() {
        var spec = IntSetting.of(INFO, 50, 0, 100).withFormat("myaddon.percent");
        assertThat(spec.normalize(150)).isEqualTo(100);
        assertThat(spec.normalize(-3)).isZero();
        assertThat(spec.format()).isEqualTo(Optional.of("myaddon.percent"));
        assertThatThrownBy(() -> IntSetting.of(INFO, 200, 0, 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IntSetting.of(INFO, 5, 10, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enumsKnowTheirConstants() {
        var spec = new EnumSetting<>(INFO, RepeatMode.OFF, mode -> Text.literal(mode.name()));
        assertThat(spec.type()).isEqualTo(RepeatMode.class);
        assertThat(spec.values()).containsExactly(RepeatMode.values());
        assertThat(spec.normalize(RepeatMode.ONE)).isEqualTo(RepeatMode.ONE);
        assertThat(spec.names().apply(RepeatMode.ALL)).isEqualTo(Text.literal("ALL"));
    }

    @Test
    void choicesKeepAnIdNobodyOffersAnyMore() {
        var spec = new ChoiceSetting(INFO, "a", () -> List.of(new ChoiceSetting.Choice("a", Text.literal("A"))));
        assertThat(spec.normalize(" gone:theme ")).isEqualTo("gone:theme");
        assertThat(spec.normalize("")).isEqualTo("a");
        assertThat(spec.choices().get()).extracting(ChoiceSetting.Choice::id).containsExactly("a");
    }

    @Test
    void textIsOneLineAndBounded() {
        var spec = new StringSetting(INFO, "", 4);
        assertThat(spec.normalize("ab\ncdef")).isEqualTo("ab c");
        assertThatThrownBy(() -> new StringSetting(INFO, "toolong", 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StringSetting(INFO, "", 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listsAreTrimmedWithoutRepeats() {
        var spec = new StringListSetting(INFO, List.of(" a ", "", "a", "b"));
        assertThat(spec.defaultValue()).containsExactly("a", "b");
        assertThat(spec.normalize(List.of("c", " c"))).containsExactly("c");
        assertThat(StringListSetting.parse("x, y,,x ,")).containsExactly("x", "y");
        assertThat(StringListSetting.join(List.of("x", "y"))).isEqualTo("x, y");
    }
}
