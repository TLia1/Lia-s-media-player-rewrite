package com.lia.mediaplayer.ui.testing;

import com.lia.mediaplayer.ui.UiHost;
import com.lia.mediaplayer.ui.screen.UiScreen;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** A host that only remembers which of the mod's screens is open. */
public final class FakeHost implements UiHost {

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
}
