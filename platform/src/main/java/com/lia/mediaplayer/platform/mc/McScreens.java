package com.lia.mediaplayer.platform.mc;

import com.lia.mediaplayer.core.platform.TextMeasurer;
import com.lia.mediaplayer.ui.UiHost;
import com.lia.mediaplayer.ui.screen.ScreenContext;
import com.lia.mediaplayer.ui.screen.UiScreen;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/** Which screen is open, as the UI sees it, how the UI's own screens are shown, and the chat under the cursor. */
public final class McScreens implements UiHost {

    private final McTextures textures;
    private final TextMeasurer measurer;
    private final McChat chat;

    McScreens(McTextures textures, TextMeasurer measurer, McChat chat) {
        this.textures = textures;
        this.measurer = measurer;
        this.chat = chat;
    }

    /** What {@code screen} is to the UI; {@code null} is no screen at all. */
    public static ScreenContext context(@Nullable Screen screen) {
        return switch (screen) {
            case null -> ScreenContext.NONE;
            case ChatScreen chat -> new ScreenContext(ScreenContext.Kind.CHAT, chatFieldEmpty(chat));
            case HostScreen host -> new ScreenContext(ScreenContext.Kind.HOSTED, !host.hosted().isTyping());
            case PauseScreen pause -> new ScreenContext(ScreenContext.Kind.PAUSE, true);
            default -> new ScreenContext(ScreenContext.Kind.OTHER, true);
        };
    }

    /** The screen open now. */
    public static ScreenContext current() {
        return context(open());
    }

    /** The vanilla screen open now, if any. 26.2 moved it from the client to its GUI. */
    static @Nullable Screen open() {
        //? if <26.2 {
        return Minecraft.getInstance().screen;
        //?} else
        /*return Minecraft.getInstance().gui.screen();*/
    }

    /** Opens {@code screen}, or closes the open one for {@code null}. */
    static void show(@Nullable Screen screen) {
        //? if <26.2 {
        Minecraft.getInstance().setScreen(screen);
        //?} else
        /*Minecraft.getInstance().gui.setScreen(screen);*/
    }

    /**
     * Whether the chat's text field is empty. The field is protected; it is read the way a
     * screen exposes its widgets, through {@code children()}.
     */
    private static boolean chatFieldEmpty(ChatScreen chat) {
        for (GuiEventListener child : chat.children()) {
            if (child instanceof EditBox box) {
                return box.getValue().isEmpty();
            }
        }
        return true;
    }

    @Override
    public void openScreen(UiScreen screen) {
        show(host(screen, null));
    }

    @Override
    public void openScreenOver(UiScreen screen) {
        show(host(screen, open()));
    }

    /** The vanilla screen that shows {@code screen} and goes back to {@code parent} when closed. */
    HostScreen host(UiScreen screen, @Nullable Screen parent) {
        return new HostScreen(screen, textures, measurer, parent);
    }

    @Override
    public void closeScreen() {
        if (open() instanceof HostScreen host) {
            host.onClose();
        }
    }

    @Override
    public Optional<UiScreen> currentScreen() {
        return open() instanceof HostScreen host ? Optional.of(host.hosted()) : Optional.empty();
    }

    @Override
    public Optional<String> chatLinkAt(double x, double y) {
        return chat.linkAt(x, y);
    }
}
