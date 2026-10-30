package com.lia.mediaplayer.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lia.mediaplayer.api.LiasMediaPlayerApi.PendingAddon;
import com.lia.mediaplayer.api.event.EventSubscriptions;
import com.lia.mediaplayer.api.event.MediaEvent;
import com.lia.mediaplayer.api.event.Subscription;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AddonApiTest {

    private static final MediaPlayerAddon NOTHING = context -> {};

    @AfterEach
    void reset() {
        LiasMediaPlayerApi.resetForTests();
    }

    @Test
    void idsAreOwnerColonName() {
        assertThat(Ids.isNamespaced("myaddon:share")).isTrue();
        assertThat(Ids.isNamespaced("my-addon.x:a/b_c")).isTrue();
        assertThat(Ids.isNamespaced("share")).isFalse();
        assertThat(Ids.isNamespaced("MyAddon:share")).isFalse();
        assertThat(Ids.isNamespaced(":share")).isFalse();
        assertThat(Ids.owner("myaddon:share")).isEqualTo("myaddon");
        assertThat(Ids.isOwner("my_addon-2")).isTrue();
        assertThat(Ids.isOwner("my addon")).isFalse();
        assertThat(Ids.require("a:b", "Thing")).isEqualTo("a:b");
        assertThatThrownBy(() -> Ids.require("b", "Thing id"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Thing id");
        assertThatThrownBy(() -> Ids.owner("nothing")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addonsHandedOverWaitUntilTheModTakesThemThenTheDoorCloses() {
        LiasMediaPlayerApi.registerAddon("first", NOTHING);
        LiasMediaPlayerApi.registerAddon("second", NOTHING);

        List<PendingAddon> taken = LiasMediaPlayerApi.takePending();
        assertThat(taken).extracting(PendingAddon::addonId).containsExactly("first", "second");
        assertThat(LiasMediaPlayerApi.takePending()).isEmpty();
        assertThatThrownBy(() -> LiasMediaPlayerApi.registerAddon("late", NOTHING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("constructor");
    }

    @Test
    void anAddonIdMustBeAnIdOwner() {
        assertThatThrownBy(() -> LiasMediaPlayerApi.registerAddon("My Addon", NOTHING))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PendingAddon("a:b", NOTHING)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onAnyListensToEveryEvent() {
        var types = new ArrayList<Class<?>>();
        EventSubscriptions events = new EventSubscriptions() {
            @Override
            public <E extends MediaEvent> Subscription on(Class<E> type, Consumer<? super E> listener) {
                types.add(type);
                return () -> {};
            }
        };
        events.onAny(event -> {}).close();
        assertThat(types).containsExactly(MediaEvent.class);
    }
}
