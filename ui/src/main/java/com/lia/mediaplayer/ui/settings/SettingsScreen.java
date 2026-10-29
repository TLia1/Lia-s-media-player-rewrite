package com.lia.mediaplayer.ui.settings;

import com.lia.mediaplayer.api.settings.BoolSetting;
import com.lia.mediaplayer.api.settings.ChoiceSetting;
import com.lia.mediaplayer.api.settings.EnumSetting;
import com.lia.mediaplayer.api.settings.IntSetting;
import com.lia.mediaplayer.api.settings.SettingSpec;
import com.lia.mediaplayer.api.settings.StringListSetting;
import com.lia.mediaplayer.api.settings.StringSetting;
import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.platform.Platform;
import com.lia.mediaplayer.core.platform.TextMeasurer;
import com.lia.mediaplayer.core.settings.Settings;
import com.lia.mediaplayer.core.settings.SettingsRegistry;
import com.lia.mediaplayer.core.text.Keys;
import com.lia.mediaplayer.core.tools.Tool;
import com.lia.mediaplayer.core.tools.ToolState;
import com.lia.mediaplayer.ui.canvas.Canvas;
import com.lia.mediaplayer.ui.icon.Glyphs;
import com.lia.mediaplayer.ui.input.InputEvent;
import com.lia.mediaplayer.ui.screen.UiScreen;
import com.lia.mediaplayer.ui.theme.Role;
import com.lia.mediaplayer.ui.theme.Theme;
import com.lia.mediaplayer.ui.widget.Column;
import com.lia.mediaplayer.ui.widget.Constraints;
import com.lia.mediaplayer.ui.widget.IconButton;
import com.lia.mediaplayer.ui.widget.Label;
import com.lia.mediaplayer.ui.widget.RenderContext;
import com.lia.mediaplayer.ui.widget.Row;
import com.lia.mediaplayer.ui.widget.ScrollPane;
import com.lia.mediaplayer.ui.widget.Size;
import com.lia.mediaplayer.ui.widget.Slider;
import com.lia.mediaplayer.ui.widget.TextButton;
import com.lia.mediaplayer.ui.widget.TextField;
import com.lia.mediaplayer.ui.widget.Toggle;
import com.lia.mediaplayer.ui.widget.Widget;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Every setting, the mod's and the addons', generated from their specs: the groups on the left
 * ("All" first), a search box, and one row per setting with its control and a button that puts
 * it back to its default. Hovering a row shows its description, and its warning in red. A
 * change applies at once; the settings file is written a moment later.
 *
 * <p>The footer says where the tools stand and offers to update them, which is the cure for
 * most YouTube failures.
 */
public final class SettingsScreen extends UiScreen {

    static final int CONTROL_WIDTH = 110;
    private static final int GROUP_WIDTH = 100;

    private final SettingsRegistry settings;
    private final TextMeasurer measurer;
    private final List<SettingRow> rows = new ArrayList<>();
    private final TextField search;
    private final Label noMatch;
    private final Label tools;
    private final Supplier<ToolState> toolState;
    private @Nullable String group;

    /**
     * @param clipboard for the text fields
     * @param toolState where the tools stand, read each frame
     * @param updateTools starts downloading yt-dlp again
     * @param close leaves the screen
     */
    public SettingsScreen(
            Supplier<Theme> theme,
            LongSupplier nanoTime,
            SettingsRegistry settings,
            TextMeasurer measurer,
            Platform.Clipboard clipboard,
            Supplier<ToolState> toolState,
            Runnable updateTools,
            Runnable close) {
        this(theme, nanoTime, settings, measurer, clipboard, toolState, updateTools, close, new Column());
    }

    private SettingsScreen(
            Supplier<Theme> theme,
            LongSupplier nanoTime,
            SettingsRegistry settings,
            TextMeasurer measurer,
            Platform.Clipboard clipboard,
            Supplier<ToolState> toolState,
            Runnable updateTools,
            Runnable close,
            Column root) {
        super(Text.translatable(Keys.SETTINGS_TITLE), theme, nanoTime, root);
        this.settings = settings;
        this.measurer = measurer;
        this.toolState = toolState;
        root.padding(8, 12).gap(6);
        root.add(
                new Label(Text.translatable(Keys.SETTINGS_TITLE), Role.TEXT)
                        .align(Label.Align.CENTER)
                        .shadow(true),
                0,
                true);

        var middle = root.add(new Row(), 1);
        middle.gap(8);
        var groups = middle.add(new Column(), 0, true);
        groups.gap(2);
        groups.add(groupButton(null, Text.translatable(Keys.SETTINGS_ALL)));
        for (String id : settings.groups()) {
            groups.add(groupButton(id, Text.translatable(Settings.groupKey(id))));
        }

        var right = middle.add(new Column(), 1);
        right.gap(4);
        search = right.add(new TextField(clipboard, 64).placeholder(Text.translatable(Keys.SETTINGS_SEARCH)), 0, true);
        var list = new Column();
        list.gap(2);
        for (SettingSpec<?> spec : settings.specs()) {
            rows.add(list.add(new SettingRow(spec, settings, clipboard), 0, true));
        }
        right.add(new ScrollPane(list), 1);
        noMatch = right.add(
                new Label(Text.translatable(Keys.SETTINGS_NO_MATCH), Role.TEXT_SUBTLE).align(Label.Align.CENTER),
                0,
                true);

        var footer = root.add(new Row(), 0, true);
        footer.gap(6);
        tools = footer.add(new Label(Text.literal(""), Role.TEXT_SUBTLE), 1);
        footer.add(new TextButton(Text.translatable(Keys.UPDATE_TOOLS), updateTools));
        footer.add(new TextButton(Text.translatable(Keys.SETTINGS_RESET_ALL), this::resetShown));
        footer.add(new TextButton(Text.translatable(Keys.DONE), close).minWidth(60));
    }

    private TextButton groupButton(@Nullable String id, Text label) {
        return new TextButton(label, () -> group = id)
                .activeWhen(() -> Objects.equals(group, id))
                .minWidth(GROUP_WIDTH);
    }

    /** The setting rows, for tests. */
    List<SettingRow> rows() {
        return List.copyOf(rows);
    }

    TextField search() {
        return search;
    }

    void showGroup(@Nullable String id) {
        group = id;
    }

    @Override
    protected void beforeRender() {
        String query = search.text().strip().toLowerCase(Locale.ROOT);
        boolean any = false;
        for (SettingRow row : rows) {
            boolean shown = (group == null || row.spec().group().equals(group)) && row.matches(query, measurer);
            row.setVisible(shown);
            any |= shown;
        }
        noMatch.setVisible(!any);
        tools.setText(describe(toolState.get()));
    }

    /** Puts every setting on screen back to its default. */
    private void resetShown() {
        for (SettingRow row : rows) {
            if (row.isVisible()) {
                settings.reset(row.spec());
            }
        }
    }

    static Text describe(ToolState state) {
        return switch (state) {
            case ToolState.Locating locating -> Text.translatable(Keys.TOOLS_LOCATING);
            case ToolState.Downloading downloading ->
                Text.translatable(
                        Keys.TOOLS_DOWNLOADING,
                        downloading.tool().baseName(),
                        downloading.progress() < 0 ? "…" : Math.round(downloading.progress() * 100) + "%");
            case ToolState.Ready ready -> Text.translatable(Keys.TOOLS_READY);
            case ToolState.Missing missing ->
                Text.translatable(
                        Keys.TOOLS_MISSING,
                        missing.checked().keySet().stream()
                                .sorted()
                                .map(Tool::baseName)
                                .collect(Collectors.joining(", ")));
        };
    }

    /** One setting: its name, its control and its reset button. */
    static final class SettingRow extends Row {

        static final int HEIGHT = 16;

        private final SettingSpec<?> spec;
        private final Widget control;
        private @Nullable String searchText;

        SettingRow(SettingSpec<?> spec, SettingsRegistry settings, Platform.Clipboard clipboard) {
            this.spec = spec;
            padding(4, 0).gap(4);
            add(new Label(spec.label(), Role.TEXT), 1);
            control = add(control(spec, settings, clipboard), 0, true);
            add(new IconButton(Glyphs::retry, () -> settings.reset(spec))
                    .enabledWhen(() -> !settings.isDefault(spec))
                    .tooltip(Text.translatable(Keys.SETTINGS_RESET)));
        }

        SettingSpec<?> spec() {
            return spec;
        }

        Widget control() {
            return control;
        }

        boolean matches(String query, TextMeasurer measurer) {
            if (query.isEmpty()) {
                return true;
            }
            String text = searchText;
            if (text == null) {
                text = (measurer.resolve(spec.label()) + " "
                                + spec.description().map(measurer::resolve).orElse(""))
                        .toLowerCase(Locale.ROOT);
                searchText = text;
            }
            return text.contains(query);
        }

        @Override
        public Size measure(Constraints constraints) {
            return new Size(super.measure(constraints).width(), HEIGHT);
        }

        @Override
        public void render(Canvas canvas, RenderContext ctx) {
            if (ctx.hovered(bounds())) {
                canvas.fill(bounds(), ctx.color(Role.ROW_HOVER));
            }
            super.render(canvas, ctx);
        }

        @Override
        public Optional<List<Text>> tooltip(double x, double y) {
            var lines = new ArrayList<Text>();
            spec.description().ifPresent(lines::add);
            spec.warning().ifPresent(lines::add);
            return lines.isEmpty() ? Optional.empty() : Optional.of(lines);
        }

        private static Widget control(SettingSpec<?> spec, SettingsRegistry settings, Platform.Clipboard clipboard) {
            return switch (spec) {
                case BoolSetting bool -> new Toggle(() -> settings.get(bool), value -> settings.set(bool, value));
                case IntSetting integer -> new IntControl(integer, settings);
                case EnumSetting<?> choice -> enumButton(choice, settings);
                case ChoiceSetting choice ->
                    new TextButton(() -> choiceName(choice, settings.get(choice)), press -> {
                                List<ChoiceSetting.Choice> choices =
                                        choice.choices().get();
                                int at = indexOf(choices, settings.get(choice));
                                int step = press.button() == InputEvent.PRIMARY_BUTTON ? 1 : -1;
                                if (!choices.isEmpty()) {
                                    settings.set(
                                            choice,
                                            choices.get(Math.floorMod(at + step, choices.size()))
                                                    .id());
                                }
                            })
                            .minWidth(CONTROL_WIDTH);
                case StringSetting string ->
                    new SyncedField(
                            clipboard,
                            string.maxLength(),
                            () -> settings.get(string),
                            value -> settings.set(string, value));
                case StringListSetting list ->
                    new SyncedField(
                            clipboard,
                            1024,
                            () -> StringListSetting.join(settings.get(list)),
                            value -> settings.set(list, StringListSetting.parse(value)));
            };
        }

        private static <E extends Enum<E>> Widget enumButton(EnumSetting<E> spec, SettingsRegistry settings) {
            return new TextButton(() -> spec.names().apply(settings.get(spec)), press -> {
                        List<E> values = spec.values();
                        int step = press.button() == InputEvent.PRIMARY_BUTTON ? 1 : -1;
                        int at = values.indexOf(settings.get(spec));
                        settings.set(spec, values.get(Math.floorMod(at + step, values.size())));
                    })
                    .minWidth(CONTROL_WIDTH);
        }

        private static Text choiceName(ChoiceSetting spec, String id) {
            return spec.choices().get().stream()
                    .filter(choice -> choice.id().equals(id))
                    .findFirst()
                    .map(ChoiceSetting.Choice::name)
                    .orElse(Text.literal(id));
        }

        private static int indexOf(List<ChoiceSetting.Choice> choices, String id) {
            for (int i = 0; i < choices.size(); i++) {
                if (choices.get(i).id().equals(id)) {
                    return i;
                }
            }
            return -1;
        }
    }

    /** A slider over a whole number, with the number beside it. */
    static final class IntControl extends Row {

        private final IntSetting spec;
        private final SettingsRegistry settings;
        private final Label value;

        IntControl(IntSetting spec, SettingsRegistry settings) {
            this.spec = spec;
            this.settings = settings;
            gap(4);
            add(new Slider(
                    CONTROL_WIDTH - 34,
                    () -> fraction(settings.get(spec)),
                    fraction -> settings.set(spec, at(fraction)),
                    fraction -> format(at(fraction))));
            // The number takes what the slider leaves, so a long unit never squeezes the slider.
            value = add(new Label(Text.literal(""), Role.TEXT_SUBTLE).align(Label.Align.RIGHT), 1);
        }

        private double fraction(int number) {
            return spec.max() == spec.min() ? 0 : (number - spec.min()) / (double) (spec.max() - spec.min());
        }

        private int at(double fraction) {
            return (int) Math.round(spec.min() + fraction * (spec.max() - spec.min()));
        }

        private Text format(int number) {
            return spec.format()
                    .<Text>map(key -> Text.translatable(key, number))
                    .orElse(Text.literal(Integer.toString(number)));
        }

        @Override
        public Size measure(Constraints constraints) {
            return new Size(CONTROL_WIDTH, super.measure(constraints).height());
        }

        @Override
        public void render(Canvas canvas, RenderContext ctx) {
            value.setText(format(settings.get(spec)));
            super.render(canvas, ctx);
        }
    }

    /** A text field that follows its setting while it is not being typed in. */
    static final class SyncedField extends Row {

        private final TextField field;
        private final Supplier<String> current;

        SyncedField(Platform.Clipboard clipboard, int maxLength, Supplier<String> current, Consumer<String> set) {
            this.current = current;
            field = add(new TextField(clipboard, maxLength).onChange(set), 1);
            field.setText(current.get());
        }

        TextField field() {
            return field;
        }

        @Override
        public Size measure(Constraints constraints) {
            return new Size(CONTROL_WIDTH, super.measure(constraints).height());
        }

        @Override
        public void render(Canvas canvas, RenderContext ctx) {
            String value = current.get();
            if (!field.isFocused() && !field.text().equals(value)) {
                field.setText(value);
            }
            super.render(canvas, ctx);
        }
    }
}
