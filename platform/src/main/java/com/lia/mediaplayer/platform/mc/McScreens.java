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
            case HostScreen host -> new ScreenContext(ScreenContext.Kind.HOSTED, true);
            case PauseScreen pause -> new ScreenContext(ScreenContext.Kind.PAUSE, true);
            default -> new ScreenContext(ScreenContext.Kind.OTHER, true);
        };
    }

    /** The screen open now. */
    public static ScreenContext current() {
        return context(Minecraft.getInstance().screen);
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
        Minecraft.getInstance().setScreen(new HostScreen(screen, textures, measurer));
    }

    @Override
    public void closeScreen() {
        if (Minecraft.getInstance().screen instanceof HostScreen) {
            Minecraft.getInstance().setScreen(null);
        }
    }

    @Override
    public Optional<UiScreen> currentScreen() {
        return Minecraft.getInstance().screen instanceof HostScreen host ? Optional.of(host.hosted()) : Optional.empty();
    }

    @Override
    public Optional<String> chatLinkAt(double x, double y) {
        return chat.linkAt(x, y);
    }
}
