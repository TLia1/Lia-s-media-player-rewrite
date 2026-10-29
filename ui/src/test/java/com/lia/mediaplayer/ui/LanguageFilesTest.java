package com.lia.mediaplayer.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lia.mediaplayer.api.settings.EnumSetting;
import com.lia.mediaplayer.api.settings.IntSetting;
import com.lia.mediaplayer.api.settings.SettingSpec;
import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.diag.PlaybackError;
import com.lia.mediaplayer.core.link.BuiltinSources;
import com.lia.mediaplayer.core.settings.CoreSettings;
import com.lia.mediaplayer.core.settings.Settings;
import com.lia.mediaplayer.core.text.Keys;
import com.lia.mediaplayer.ui.settings.UiSettings;
import com.lia.mediaplayer.ui.theme.Theme;
import com.lia.mediaplayer.ui.theme.ThemeRegistry;
import com.lia.mediaplayer.ui.theme.Themes;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Every translation key the code uses is in every language file, no file carries a key nobody
 * uses, and each translation keeps the placeholders of the English text.
 */
class LanguageFilesTest {

    private static final Set<String> LANGUAGES = Set.of("en_us", "fr_fr");
    private static final Pattern PLACEHOLDER = Pattern.compile("%(\\d+\\$)?s|%%");

    private static Set<String> usedKeys() {
        var keys = new HashSet<>(Keys.named());
        keys.addAll(BuiltinSources.labelKeys());
        for (PlaybackError.Cause cause : PlaybackError.Cause.values()) {
            keys.add(cause.messageKey());
            keys.add(cause.hintKey());
        }
        for (Theme theme : Themes.all()) {
            keys.add(key(theme.name()));
        }
        for (Hotkey hotkey : Hotkey.values()) {
            keys.add(hotkey.translationKey());
        }
        var specs = new ArrayList<SettingSpec<?>>(CoreSettings.all());
        specs.addAll(UiSettings.all(UiSettings.theme(new ThemeRegistry())));
        for (SettingSpec<?> spec : specs) {
            keys.add(key(spec.label()));
            spec.description().ifPresent(text -> keys.add(key(text)));
            spec.warning().ifPresent(text -> keys.add(key(text)));
            keys.add(Settings.groupKey(spec.group()));
            if (spec instanceof EnumSetting<?> choice) {
                choice.values().forEach(value -> keys.add(name(choice, value)));
            }
            if (spec instanceof IntSetting integer) {
                integer.format().ifPresent(keys::add);
            }
        }
        return keys;
    }

    private static String key(Text text) {
        return ((Text.Translatable) text).key();
    }

    @SuppressWarnings("unchecked") // the values come from the setting itself
    private static <E extends Enum<E>> String name(EnumSetting<E> spec, Object value) {
        Text text = spec.names().apply((E) value);
        return text instanceof Text.Translatable translatable ? translatable.key() : Keys.DONE;
    }

    private static Map<String, String> load(String language) throws IOException {
        Path dir = Path.of(System.getProperty(
                "liasmediaplayer.langDir", "../platform/src/main/resources/assets/liasmediaplayer/lang"));
        JsonObject json = JsonParser.parseString(
                        Files.readString(dir.resolve(language + ".json"), StandardCharsets.UTF_8))
                .getAsJsonObject();
        var map = new TreeMap<String, String>();
        json.entrySet().forEach(e -> map.put(e.getKey(), e.getValue().getAsString()));
        return map;
    }

    @Test
    void everyFileHasExactlyTheKeysTheCodeUses() throws IOException {
        Set<String> used = usedKeys();
        for (String language : LANGUAGES) {
            assertThat(load(language).keySet()).as(language).containsExactlyInAnyOrderElementsOf(used);
        }
    }

    @Test
    void translationsKeepThePlaceholders() throws IOException {
        Map<String, String> english = load("en_us");
        for (String language : LANGUAGES) {
            Map<String, String> other = load(language);
            english.forEach((key, text) -> assertThat(placeholders(other.getOrDefault(key, "")))
                    .as(language + " " + key)
                    .isEqualTo(placeholders(text)));
        }
    }

    private static int placeholders(String text) {
        Matcher matcher = PLACEHOLDER.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
