package com.example.lmpaddon;

import com.lia.mediaplayer.api.AddonContext;
import com.lia.mediaplayer.api.MediaPlayer;
import com.lia.mediaplayer.api.MediaPlayerAddon;
import com.lia.mediaplayer.api.event.MediaEvent;
import com.lia.mediaplayer.api.media.MediaHandle;
import com.lia.mediaplayer.api.settings.BoolSetting;
import com.lia.mediaplayer.api.settings.SettingInfo;
import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.api.ui.ActionIcon;
import com.lia.mediaplayer.api.ui.KeyBindingSpec;
import com.lia.mediaplayer.api.ui.MediaWindow;
import com.lia.mediaplayer.api.ui.ShortcutSpec;
import com.lia.mediaplayer.api.ui.ThemeRole;
import com.lia.mediaplayer.api.ui.ThemeSpec;
import com.lia.mediaplayer.api.ui.WindowAction;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An addon for Lia's Media Player that uses nothing but its API, so this one class serves Fabric
 * and NeoForge and every Minecraft version the mod is built for.
 *
 * <p>It adds:
 *
 * <ul>
 *   <li>a link source: Internet Archive item pages ({@code archive.org/details/…}) become an
 *       {@code [archive]} label in chat and play through yt-dlp, like the built-in sites;
 *   <li>a theme, "Sunset", in the mod's theme setting;
 *   <li>a "Restart" button on video windows and audio bars, which jumps back to the start of the
 *       track — Shift also unpauses it — and a setting that hides it;
 *   <li>the same restart as a shortcut over the chat (Ctrl+R) and as a key to bind in the
 *       controls screen;
 *   <li>a line in the log for every track that starts.
 * </ul>
 *
 * <p>On Fabric it is the {@code "liasmediaplayer"} entrypoint in {@code fabric.mod.json}; on
 * NeoForge, {@link com.example.lmpaddon.neoforge.ExampleAddonMod} hands it over.
 */
public final class ExampleAddon implements MediaPlayerAddon {

    public static final String ID = "exampleaddon";

    private static final Logger LOGGER = LoggerFactory.getLogger(ExampleAddon.class);

    /** Whether the restart button shows. Stored in the mod's settings file under its id. */
    static final BoolSetting RESTART_BUTTON = new BoolSetting(
            SettingInfo.of(
                            ID + ":restart_button",
                            ID + ":general",
                            Text.translatable("settings.exampleaddon.restart_button"))
                    .withDescription(Text.translatable("settings.exampleaddon.restart_button.description")),
            true);

    static final ThemeSpec SUNSET = ThemeSpec.of(ID + ":sunset", Text.translatable("theme.exampleaddon.sunset"))
            .with(ThemeRole.WINDOW_BG, 0xD0221018)
            .with(ThemeRole.TITLE_BAR_BG, 0xF0301520)
            .with(ThemeRole.CONTROL_BAR_BG, 0xF02A1219)
            .with(ThemeRole.FILL, 0xFFFF8A50)
            .with(ThemeRole.KNOB, 0xFFFFD0B0)
            .with(ThemeRole.BORDER_FOCUSED, 0xFFFF8A50)
            .with(ThemeRole.ICON_HOVER, 0xFFFFB347)
            .with(ThemeRole.ICON_ACTIVE, 0xFFFF8A50)
            .with(ThemeRole.ROW_SELECTED, 0xFF6B3040);

    /** The running mod, once it is up; read on the client thread only. */
    private Optional<MediaPlayer> player = Optional.empty();

    @Override
    public void register(AddonContext context) {
        context.sources().register(new ArchiveOrgSource());
        context.themes().register(SUNSET);
        context.settings().register(RESTART_BUTTON);
        context.windowActions().register(new RestartAction());
        context.shortcuts().register(ShortcutSpec.ctrl(ID + ":restart", 'R', ExampleAddon::restart));
        context.keyBindings()
                .register(new KeyBindingSpec(ID + ":restart", "key.exampleaddon.restart", ExampleAddon::restart));

        context.events()
                .on(
                        MediaEvent.TrackChanged.class,
                        event -> LOGGER.info("Playback {} started {}", event.handle().id(), event.url()));
        context.onReady(mod -> {
            player = Optional.of(mod);
            LOGGER.info("Lia's Media Player API {} is up", mod.apiVersion());
        });
    }

    /** Back to the start of the track of {@code handle}; {@code false} when there is no player. */
    static boolean restart(Optional<MediaHandle> handle) {
        handle.ifPresent(playing -> playing.seek(Duration.ZERO));
        return handle.isPresent();
    }

    /** The restart button of the windows' corner row. */
    private final class RestartAction implements WindowAction {

        @Override
        public String id() {
            return ID + ":restart";
        }

        @Override
        public ActionIcon icon() {
            return ActionIcon.RETRY;
        }

        @Override
        public Text tooltip() {
            return Text.translatable("action.exampleaddon.restart");
        }

        @Override
        public boolean appliesTo(MediaWindow window) {
            // Called every frame: two field reads and a map lookup.
            boolean shown = player.map(mod -> mod.settings().get(RESTART_BUTTON)).orElse(true);
            return shown && window.handle().isPresent();
        }

        @Override
        public void onClick(MediaWindow window, boolean shift) {
            window.handle().ifPresent(handle -> {
                handle.seek(Duration.ZERO);
                if (shift) {
                    handle.play();
                }
            });
        }
    }
}
