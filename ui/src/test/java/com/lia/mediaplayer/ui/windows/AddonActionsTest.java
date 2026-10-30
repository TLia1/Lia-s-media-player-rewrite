package com.lia.mediaplayer.ui.windows;

import static org.assertj.core.api.Assertions.assertThat;

import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.api.ui.ActionIcon;
import com.lia.mediaplayer.api.ui.MediaWindow;
import com.lia.mediaplayer.api.ui.WindowAction;
import com.lia.mediaplayer.core.addon.AddonGuard;
import com.lia.mediaplayer.core.testing.FakePlatform;
import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.input.InputEvent;
import com.lia.mediaplayer.ui.input.Mods;
import com.lia.mediaplayer.ui.testing.FakePlayback;
import com.lia.mediaplayer.ui.testing.RecordingCanvas;
import com.lia.mediaplayer.ui.theme.Themes;
import com.lia.mediaplayer.ui.widget.IconButton;
import com.lia.mediaplayer.ui.widget.RenderContext;
import com.lia.mediaplayer.ui.widget.Widget;
import com.lia.mediaplayer.ui.window.WindowManager;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The addons' buttons in the corner row of the windows. */
class AddonActionsTest {

    @TempDir
    Path gameDir;

    private final WindowManager windows = new WindowManager();
    private final RecordingCanvas canvas = new RecordingCanvas(640, 360);
    private final AddonGuard guard = new AddonGuard(3);
    private final List<String> clicks = new ArrayList<>();

    /** An action that notes its clicks, and shows where {@code applies} says. */
    private WindowAction action(String id, Predicate<MediaWindow> applies) {
        return new WindowAction() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public ActionIcon icon() {
                return ActionIcon.RETRY;
            }

            @Override
            public Text tooltip() {
                return Text.literal("Tooltip of " + id);
            }

            @Override
            public boolean appliesTo(MediaWindow window) {
                return applies.test(window);
            }

            @Override
            public void onClick(MediaWindow window, boolean shift) {
                clicks.add(id + " " + window.kind() + " " + window.url().orElse("-") + (shift ? " shift" : ""));
            }
        };
    }

    private WindowServices services(List<WindowAction> actions) {
        return WindowServices.of(new FakePlatform(gameDir)).withActions(new AddonActions(() -> actions, guard));
    }

    private void render() {
        windows.render(canvas.clear(), new RenderContext(Themes.DARK, 0, -1, -1, true));
    }

    /** The widget just left of {@code of} in its row, or null. */
    private static @Nullable Widget leftOf(com.lia.mediaplayer.ui.window.Window window, Widget of) {
        Rect b = of.bounds();
        for (int dx = 1; dx < 6; dx++) {
            Widget found = window.widgetAt(b.x() - dx, b.centerY());
            if (found instanceof IconButton && !found.equals(of)) {
                return found;
            }
        }
        return null;
    }

    private void click(Widget target, Mods mods) {
        Rect b = target.bounds();
        windows.input(new InputEvent.PointerDown(b.centerX(), b.centerY(), 0, mods, 1));
        windows.input(new InputEvent.PointerUp(b.centerX(), b.centerY(), 0, mods));
    }

    @Test
    void anAddonButtonSitsLeftOfTheHeartAndIsToldOfItsWindow() {
        var playback = new FakePlayback();
        var window = windows.open(new VideoWindow(playback, services(List.of(action("myaddon:a", w -> true)))));
        render();
        Widget heart = window.titleButtons().getFirst();
        Widget button = leftOf(window, heart);
        assertThat(button).isNotNull();
        assertThat(button.tooltip(0, 0).orElseThrow()).containsExactly(Text.literal("Tooltip of myaddon:a"));

        click(button, Mods.NONE);
        click(button, Mods.shiftOnly());
        String url = playback.currentUrl().orElse("-");
        assertThat(clicks).containsExactly("myaddon:a VIDEO " + url, "myaddon:a VIDEO " + url + " shift");
    }

    @Test
    void aButtonShowsOnlyOnTheWindowsItAppliesTo() {
        var actions = List.of(action("myaddon:players", w -> w.handle().isEmpty() && w.kind() != MediaKind.IMAGE));
        var video = windows.open(new VideoWindow(new FakePlayback(), services(actions)));
        render();
        // The fake playback has no handle, so the button applies; on a picture it does not.
        assertThat(leftOf(video, video.titleButtons().getFirst())).isNotNull();

        var ref = new MediaRef("https://example.com/cat.png", MediaKind.IMAGE, "test:picture", Text.literal("[p]"));
        var picture = AddonActions.picture(ref);
        assertThat(picture.url()).contains(ref.url());
        assertThat(picture.handle()).isEmpty();
        var buttons = new AddonActions(() -> actions, guard).buttons(picture);
        assertThat(buttons)
                .singleElement()
                .satisfies(b -> assertThat(b.shown().getAsBoolean()).isFalse());
    }

    @Test
    void atMostThreeAddonButtonsPerWindow() {
        var actions = List.of(
                action("myaddon:a", w -> true),
                action("myaddon:b", w -> true),
                action("myaddon:c", w -> true),
                action("myaddon:d", w -> true));
        var buttons = new AddonActions(() -> actions, guard)
                .buttons(AddonActions.player(MediaKind.AUDIO, new FakePlayback()));
        assertThat(buttons).hasSize(AddonActions.MAX_PER_WINDOW);
        assertThat(AddonActions.NONE.buttons(AddonActions.player(MediaKind.AUDIO, new FakePlayback())))
                .isEmpty();
    }

    @Test
    void aButtonThatKeepsFailingHidesForGood() {
        var broken = action("myaddon:broken", w -> {
            throw new IllegalStateException("bug");
        });
        var button = new AddonActions(() -> List.of(broken), guard)
                .buttons(AddonActions.player(MediaKind.VIDEO, new FakePlayback()))
                .getFirst();
        for (int i = 0; i < 5; i++) {
            assertThat(button.shown().getAsBoolean()).isFalse();
        }
        assertThat(guard.failures("myaddon:broken")).isEqualTo(3);
        assertThat(guard.isDisabled("myaddon:broken")).isTrue();
    }

    @Test
    void everyIconIsOneOfTheModsGlyphs() {
        for (ActionIcon icon : ActionIcon.values()) {
            AddonActions.icon(icon).draw(canvas.clear(), 0, 0, 0xFFFFFFFF);
            assertThat(canvas.fills()).as(icon.name()).isNotEmpty();
        }
    }
}
