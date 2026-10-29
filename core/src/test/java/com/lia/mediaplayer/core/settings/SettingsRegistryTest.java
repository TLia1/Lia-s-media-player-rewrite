package com.lia.mediaplayer.core.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonElement;
import com.lia.mediaplayer.api.settings.BoolSetting;
import com.lia.mediaplayer.api.settings.ChoiceSetting;
import com.lia.mediaplayer.api.settings.IntSetting;
import com.lia.mediaplayer.api.settings.SettingInfo;
import com.lia.mediaplayer.api.settings.StringListSetting;
import com.lia.mediaplayer.api.settings.StringSetting;
import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.policy.LinkFilter;
import com.lia.mediaplayer.core.storage.JsonStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsRegistryTest {

    private static final IntSetting VOLUME =
            IntSetting.of(SettingInfo.of("test:volume", "test:sound", Text.literal("Volume")), 50, 0, 100);
    private static final BoolSetting LOUD =
            new BoolSetting(SettingInfo.of("test:loud", "test:sound", Text.literal("Loud")), false);

    @TempDir
    Path dir;

    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    private JsonStore<Map<String, JsonElement>> store;

    @AfterEach
    void stopTimer() {
        timer.shutdownNow();
    }

    private SettingsRegistry registry() {
        store = new JsonStore<>(
                dir.resolve("settings.json"),
                SettingsRegistry.codec(),
                1,
                List.of(),
                Runnable::run,
                timer,
                Duration.ofHours(1));
        return new SettingsRegistry(store);
    }

    @Test
    void valuesStartAtTheirDefaultAndSurviveARestart() {
        var settings = registry();
        settings.register(VOLUME);
        assertThat(settings.get(VOLUME)).isEqualTo(50);
        assertThat(settings.isDefault(VOLUME)).isTrue();
        settings.set(VOLUME, 80);
        store.flush();

        var again = registry();
        again.register(VOLUME);
        assertThat(again.get(VOLUME)).isEqualTo(80);
        assertThat(again.isDefault(VOLUME)).isFalse();
    }

    @Test
    void valuesAreNormalised() throws IOException {
        Files.writeString(dir.resolve("settings.json"), "{\"schema\":1,\"values\":{\"test:volume\": 900}}");
        var settings = registry();
        settings.register(VOLUME);
        assertThat(settings.get(VOLUME)).isEqualTo(100);
        settings.set(VOLUME, -4);
        assertThat(settings.get(VOLUME)).isZero();
    }

    @Test
    void aStoredValueOfTheWrongShapeKeepsTheDefault() throws IOException {
        Files.writeString(dir.resolve("settings.json"), "{\"schema\":1,\"values\":{\"test:volume\": \"loud\"}}");
        var settings = registry();
        settings.register(VOLUME);
        assertThat(settings.get(VOLUME)).isEqualTo(50);
    }

    @Test
    void anUnknownIdIsKeptForWhenItsOwnerReturns() throws IOException {
        Files.writeString(dir.resolve("settings.json"), "{\"schema\":1,\"values\":{\"gone:thing\": 3}}");
        var settings = registry();
        settings.register(VOLUME);
        settings.set(VOLUME, 10);
        store.flush();
        assertThat(Files.readString(dir.resolve("settings.json"))).contains("gone:thing");
    }

    @Test
    void listenersHearChangesOnly() {
        var settings = registry();
        settings.register(VOLUME);
        settings.register(LOUD);
        var heard = new ArrayList<Object>();
        var subscription = settings.apply(VOLUME, heard::add);
        settings.onChange(LOUD, heard::add);
        settings.set(VOLUME, 50);
        settings.set(VOLUME, 60);
        settings.set(LOUD, true);
        settings.reset(VOLUME);
        subscription.close();
        settings.set(VOLUME, 70);
        assertThat(heard).containsExactly(50, 60, true, 50);
    }

    @Test
    void aFailingListenerDoesNotStopTheOthers() {
        var settings = registry();
        settings.register(LOUD);
        var heard = new ArrayList<Boolean>();
        settings.onChange(LOUD, value -> {
            throw new IllegalStateException("boom");
        });
        settings.onChange(LOUD, heard::add);
        settings.set(LOUD, true);
        assertThat(heard).containsExactly(true);
    }

    @Test
    void registeringTwiceOrReadingUnknownFails() {
        var settings = registry();
        settings.register(VOLUME);
        assertThatThrownBy(() -> settings.register(VOLUME)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> settings.get(LOUD)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyTypeRoundTripsThroughTheFile() {
        var info = SettingInfo.of("test:x", "test:g", Text.literal("x"));
        var choice = new ChoiceSetting(info, "a", () -> List.of(new ChoiceSetting.Choice("a", Text.literal("A"))));
        var string = new StringSetting(info, "", 5);
        var list = new StringListSetting(info, List.of());
        var mode = CoreSettings.LINK_FILTER;
        assertThat(SettingsRegistry.decode(choice, SettingsRegistry.encode(choice, "b")))
                .isEqualTo("b");
        assertThat(string.normalize("one\ntwo three")).isEqualTo("one t");
        assertThat(SettingsRegistry.decode(list, SettingsRegistry.encode(list, List.of("a", "b"))))
                .isEqualTo(List.of("a", "b"));
        assertThat(SettingsRegistry.decode(mode, SettingsRegistry.encode(mode, LinkFilter.Mode.ALLOWLIST)))
                .isEqualTo(LinkFilter.Mode.ALLOWLIST);
        assertThat(choice.normalize("  ")).isEqualTo("a");
    }

    @Test
    void groupsKeepTheOrderOfTheirFirstSetting() {
        var settings = registry();
        CoreSettings.all().forEach(settings::register);
        assertThat(settings.groups())
                .containsExactly(Settings.VIDEO, Settings.CHAT, Settings.PERFORMANCE, Settings.TOOLS);
        assertThat(settings.spec(VOLUME.id())).isEmpty();
        assertThat(Settings.groupKey(Settings.VIDEO)).isEqualTo("settings.liasmediaplayer.group.video");
    }

    @Test
    void theChatSettingsMakeTheLinkFilter() {
        var settings = registry();
        CoreSettings.all().forEach(settings::register);
        var filters = new ArrayList<LinkFilter>();
        settings.applyAll(
                List.of(CoreSettings.LINK_FILTER, CoreSettings.BLOCKED_HOSTS),
                () -> filters.add(CoreSettings.linkFilter(settings)));
        settings.set(CoreSettings.BLOCKED_HOSTS, StringListSetting.parse(" example.com, ,i.redd.it,example.com"));
        settings.set(CoreSettings.LINK_FILTER, LinkFilter.Mode.BLOCKLIST);
        assertThat(filters).hasSize(3);
        assertThat(filters.getLast().allowsUrl("https://example.com/a.png")).isFalse();
        assertThat(settings.get(CoreSettings.BLOCKED_HOSTS)).containsExactly("example.com", "i.redd.it");
        assertThat(StringListSetting.join(settings.get(CoreSettings.BLOCKED_HOSTS)))
                .isEqualTo("example.com, i.redd.it");
    }

    @Test
    void qualitiesRoundDown() {
        assertThat(VideoQuality.atMost(144)).isEqualTo(VideoQuality.P360);
        assertThat(VideoQuality.atMost(719)).isEqualTo(VideoQuality.P480);
        assertThat(VideoQuality.atMost(4000)).isEqualTo(VideoQuality.P1080);
        assertThat(VideoQuality.P720.label()).isEqualTo(Text.literal("720p"));
    }
}
