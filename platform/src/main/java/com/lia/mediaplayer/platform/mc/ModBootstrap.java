package com.lia.mediaplayer.platform.mc;

import com.lia.mediaplayer.api.LiasMediaPlayerApi.PendingAddon;
import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.MediaPlayerCore;
import com.lia.mediaplayer.ui.ButtonSink;
import com.lia.mediaplayer.ui.ClientEvents;
import com.lia.mediaplayer.ui.MediaPlayerUi;
import com.lia.mediaplayer.ui.input.InputEvent;
import com.lia.mediaplayer.ui.screen.ScreenContext;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The running mod: the engine and the UI on top of it, built from a platform, plus the
 * translation of the game's moments into the UI's terms — a screen into a {@link
 * ScreenContext}, a {@link GuiGraphics} into a canvas, key codes into events.
 *
 * <p>Each loader's entrypoint creates one, keeps it in a field, and forwards its events to
 * it. The entrypoints make no decisions; this class makes none either, beyond translating.
 */
public final class ModBootstrap implements AutoCloseable {

    private final McPlatform platform;
    private final MediaPlayerCore core;
    private final MediaPlayerUi ui;
    private final McKeyMappings keys = new McKeyMappings();
    private final McChat chat = new McChat();
    private final McScreens screens;

    private ModBootstrap(McPlatform platform) {
        this.platform = platform;
        this.core = new MediaPlayerCore(platform);
        this.screens = new McScreens(platform.textures(), platform.text(), chat);
        this.ui = new MediaPlayerUi(core, screens);
        McChat.install(ui);
    }

    public static ModBootstrap start(McPlatform platform) {
        return new ModBootstrap(platform);
    }

    public ClientEvents events() {
        return ui;
    }

    /**
     * Registers the addons, once. Each loader calls it at the moment it collects key mappings,
     * before {@link #keyMappings()}, so the addons' key bindings are among them.
     */
    public void loadAddons(List<PendingAddon> addons) {
        ui.loadAddons(addons);
        keys.addAddonBindings(ui.addons().keyBindings());
    }

    /** The key mappings to register with the game: the mod's, then the addons'. */
    public List<KeyMapping> keyMappings() {
        return keys.all();
    }

    /** The mod's settings, opened from the game's mod list; closing them goes back to {@code parent}. */
    public Screen settingsScreen(Screen parent) {
        return screens.host(ui.settingsScreen(), parent);
    }

    /** The client commands, for a dispatcher of source type {@code S}. */
    public <S> LiteralArgumentBuilder<S> showCommand(BiConsumer<CommandContext<S>, Component> fail) {
        return McCommands.show(ui.showCommand(), fail);
    }

    /** End of a client tick: between two frames, the moment released textures are freed. */
    public void tick() {
        platform.textures().flushReleases();
        chat.tick();
        keys.poll(ui);
        ui.tick();
    }

    public void renderScreen(Screen screen, GuiGraphics graphics, int mouseX, int mouseY) {
        var canvas = new McCanvas(graphics, platform.textures(), platform.text());
        ui.renderScreen(McScreens.context(screen), canvas, mouseX, mouseY, McInput.currentMods());
        canvas.finish();
    }

    public void renderHud(GuiGraphics graphics) {
        var canvas = new McCanvas(graphics, platform.textures(), platform.text());
        ui.renderHud(McScreens.current(), canvas);
        canvas.finish();
    }

    /** @return whether the mod took the press */
    public boolean mousePressed(Screen screen, double x, double y, int button) {
        return ui.input(McScreens.context(screen), McInput.press(x, y, button));
    }

    public boolean mouseReleased(Screen screen, double x, double y, int button) {
        return ui.input(McScreens.context(screen), McInput.release(x, y, button));
    }

    public boolean mouseScrolled(Screen screen, double x, double y, double delta) {
        return ui.input(McScreens.context(screen), McInput.scroll(x, y, delta));
    }

    public boolean keyPressed(Screen screen, int keyCode, int scanCode, int modifiers) {
        ScreenContext context = McScreens.context(screen);
        // The game counts mapped keys only in the world; over the mod's own screens a mapped
        // key still means its hotkey, so the key that opened a screen also closes it — unless
        // it is being typed into a text field.
        if (context.kind() == ScreenContext.Kind.HOSTED
                && context.chatFieldEmpty()
                && keys.pressedOverScreen(keyCode, scanCode, ui)) {
            return true;
        }
        return ui.input(context, McInput.key(keyCode, modifiers));
    }

    public boolean charTyped(Screen screen, char codePoint) {
        return ui.input(McScreens.context(screen), new InputEvent.CharTyped(codePoint));
    }

    /**
     * A screen was built. {@code addWidget} is the loader's way of adding a widget to a screen
     * that is already built.
     */
    public void screenOpened(Screen screen, Consumer<AbstractWidget> addWidget) {
        ui.screenOpened(McScreens.context(screen), new ButtonSink() {
            @Override
            public int screenWidth() {
                return screen.width;
            }

            @Override
            public void add(Text label, int x, int y, int width, int height, Runnable onPress) {
                addWidget.accept(Button.builder(McText.toComponent(label), button -> onPress.run())
                        .bounds(x, y, width, height)
                        .build());
            }
        });
    }

    public void worldLeft() {
        ui.worldLeft();
    }

    @Override
    public void close() {
        McChat.install(null);
        core.close();
        platform.textures().close();
    }
}
