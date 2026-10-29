package com.lia.mediaplayer.ui.shortcut;

import static com.lia.mediaplayer.ui.shortcut.ShortcutTable.Action.LOOP;
import static com.lia.mediaplayer.ui.shortcut.ShortcutTable.Action.MUTE;
import static com.lia.mediaplayer.ui.shortcut.ShortcutTable.Action.NEXT;
import static com.lia.mediaplayer.ui.shortcut.ShortcutTable.Action.PLAY_PAUSE;
import static com.lia.mediaplayer.ui.shortcut.ShortcutTable.Action.PREVIOUS;
import static com.lia.mediaplayer.ui.shortcut.ShortcutTable.Action.SEEK_BACK;
import static com.lia.mediaplayer.ui.shortcut.ShortcutTable.Action.SEEK_BACK_FAR;
import static com.lia.mediaplayer.ui.shortcut.ShortcutTable.Action.SEEK_FORWARD;
import static com.lia.mediaplayer.ui.shortcut.ShortcutTable.Action.SEEK_FORWARD_FAR;
import static com.lia.mediaplayer.ui.shortcut.ShortcutTable.Action.SHUFFLE;
import static com.lia.mediaplayer.ui.shortcut.ShortcutTable.Action.THEATER;
import static com.lia.mediaplayer.ui.shortcut.ShortcutTable.Action.VOLUME_DOWN;
import static com.lia.mediaplayer.ui.shortcut.ShortcutTable.Action.VOLUME_UP;
import static org.assertj.core.api.Assertions.assertThat;

import com.lia.mediaplayer.ui.input.Key;
import com.lia.mediaplayer.ui.input.Mods;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ShortcutTableTest {

    private static final Mods CTRL = Mods.ctrlOnly();
    private static final Mods CMD_ON_MAC = new Mods(false, false, false, true, true);
    private static final Mods CTRL_ON_MAC = new Mods(false, true, false, false, true);

    private static Optional<ShortcutTable.Action> idle(Key key, Mods mods) {
        return ShortcutTable.actionFor(key, mods, false);
    }

    @Test
    void bareKeysOnlyWhileNothingIsTyped() {
        assertThat(idle(Key.SPACE, Mods.NONE)).contains(PLAY_PAUSE);
        assertThat(idle(Key.LEFT, Mods.NONE)).contains(SEEK_BACK);
        assertThat(idle(Key.RIGHT, Mods.NONE)).contains(SEEK_FORWARD);
        assertThat(idle(Key.LEFT, Mods.shiftOnly())).contains(SEEK_BACK_FAR);
        assertThat(idle(Key.RIGHT, Mods.shiftOnly())).contains(SEEK_FORWARD_FAR);
        assertThat(ShortcutTable.actionFor(Key.SPACE, Mods.NONE, true)).isEmpty();
        assertThat(ShortcutTable.actionFor(Key.LEFT, Mods.NONE, true)).isEmpty();
        assertThat(idle(Key.SPACE, Mods.shiftOnly())).isEmpty();
        assertThat(idle(Key.UP, Mods.NONE)).as("bare up recalls chat history").isEmpty();
        assertThat(idle(Key.N, Mods.NONE)).as("a bare letter is text").isEmpty();
        assertThat(idle(Key.SPACE, new Mods(false, false, true, false, false))).isEmpty();
    }

    @Test
    void thePrimaryModifierAlwaysWorks() {
        assertThat(ShortcutTable.actionFor(Key.M, CTRL, true)).contains(MUTE);
        assertThat(idle(Key.L, CTRL)).contains(LOOP);
        assertThat(idle(Key.S, CTRL)).contains(SHUFFLE);
        assertThat(idle(Key.N, CTRL)).contains(NEXT);
        assertThat(idle(Key.P, CTRL)).contains(PREVIOUS);
        assertThat(idle(Key.F, CTRL)).contains(THEATER);
        assertThat(idle(Key.UP, CTRL)).contains(VOLUME_UP);
        assertThat(idle(Key.DOWN, CTRL)).contains(VOLUME_DOWN);
        assertThat(idle(Key.A, CTRL)).as("select all in the chat field").isEmpty();
        assertThat(idle(Key.N, CMD_ON_MAC)).contains(NEXT);
        assertThat(idle(Key.N, CTRL_ON_MAC)).isEmpty();
    }

    @Test
    void escapeIsNeverTaken() {
        assertThat(idle(Key.ESCAPE, Mods.NONE)).isEmpty();
        assertThat(idle(Key.ESCAPE, CTRL)).isEmpty();
    }
}
