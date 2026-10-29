package com.lia.mediaplayer.ui.testing;

import com.lia.mediaplayer.ui.UiHost;
import com.lia.mediaplayer.ui.screen.UiScreen;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** A host that remembers which of the mod's screens is open, and has chat links where a test puts them. */
public final class FakeHost implements UiHost {

    private final Map<String, String> chatLinks = new HashMap<>();
    private @Nullable UiScreen open;
    private int opened;

    @Override
    public void openScreen(UiScreen screen) {
        open = screen;
        opened++;
    }

    @Override
    public void closeScreen() {
        UiScreen current = open;
        open = null;
        if (current != null) {
            current.removed();
        }
    }

    @Override
    public Optional<UiScreen> currentScreen() {
        return Optional.ofNullable(open);
    }

    public int opened() {
        return opened;
    }

    /** Puts a chat link at {@code (x, y)}, as the chat would have drawn one there. */
    public void chatLink(double x, double y, String url) {
        chatLinks.put(x + "," + y, url);
    }

    @Override
    public Optional<String> chatLinkAt(double x, double y) {
        return Optional.ofNullable(chatLinks.get(x + "," + y));
    }
}
