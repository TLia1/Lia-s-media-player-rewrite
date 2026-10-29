package com.lia.mediaplayer.ui.testing;

import com.lia.mediaplayer.ui.UiHost;
import com.lia.mediaplayer.ui.screen.UiScreen;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** A host that remembers which of the mod's screens is open, and has chat links where a test puts them. */
public final class FakeHost implements UiHost {

    private final Map<String, String> chatLinks = new HashMap<>();
    private final Deque<UiScreen> stack = new ArrayDeque<>();
    private int opened;

    @Override
    public void openScreen(UiScreen screen) {
        while (!stack.isEmpty()) {
            stack.pop().removed();
        }
        stack.push(screen);
        opened++;
    }

    @Override
    public void openScreenOver(UiScreen screen) {
        stack.push(screen);
        opened++;
    }

    @Override
    public void closeScreen() {
        if (!stack.isEmpty()) {
            stack.pop().removed();
        }
    }

    @Override
    public Optional<UiScreen> currentScreen() {
        return Optional.ofNullable(stack.peek());
    }

    /** How many screens are open, the one on top and those it was opened over. */
    public int depth() {
        return stack.size();
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
