package com.example.lmpaddon;

import static org.assertj.core.api.Assertions.assertThat;

import com.lia.mediaplayer.api.AddonContext;
import com.lia.mediaplayer.api.MediaPlayer;
import com.lia.mediaplayer.api.Registry;
import com.lia.mediaplayer.api.event.EventSubscriptions;
import com.lia.mediaplayer.api.event.MediaEvent;
import com.lia.mediaplayer.api.event.Subscription;
import com.lia.mediaplayer.api.media.MediaHandle;
import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.policy.Interceptor;
import com.lia.mediaplayer.api.settings.SettingSpec;
import com.lia.mediaplayer.api.source.LinkSource;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.api.source.Resolver;
import com.lia.mediaplayer.api.ui.KeyBindingSpec;
import com.lia.mediaplayer.api.ui.MediaWindow;
import com.lia.mediaplayer.api.ui.ShortcutSpec;
import com.lia.mediaplayer.api.ui.ThemeSpec;
import com.lia.mediaplayer.api.ui.WindowAction;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * An addon is plain Java, so it is tested without the game: hand {@code register} a context
 * that records what it is given.
 */
class ExampleAddonTest {

    private final Recording context = new Recording();

    @Test
    void registersASourceAThemeAndAWindowButton() {
        new ExampleAddon().register(context);

        assertThat(context.sources).hasSize(1);
        assertThat(context.themes).extracting(ThemeSpec::id).containsExactly("exampleaddon:sunset");
        assertThat(context.windowActions).extracting(WindowAction::id).containsExactly("exampleaddon:restart");
        assertThat(context.settings).extracting(SettingSpec::id).containsExactly("exampleaddon:restart_button");
        assertThat(context.shortcuts).singleElement().satisfies(shortcut -> {
            assertThat(shortcut.key()).isEqualTo('R');
            assertThat(shortcut.shift()).isFalse();
        });
        assertThat(context.keyBindings).extracting(KeyBindingSpec::nameKey).containsExactly("key.exampleaddon.restart");
        assertThat(context.ready).hasSize(1);
    }

    @Test
    void claimsInternetArchiveItemPagesOnly() {
        LinkSource source = new ArchiveOrgSource();
        assertThat(match(source, "https://archive.org/details/night_of_the_living_dead"))
                .hasValueSatisfying(ref -> {
                    assertThat(ref.kind()).isEqualTo(MediaKind.VIDEO);
                    assertThat(ref.sourceId()).isEqualTo("exampleaddon:archive_org");
                });
        assertThat(match(source, "https://www.archive.org/details/x")).isPresent();
        assertThat(match(source, "https://archive.org/details/")).isEmpty();
        assertThat(match(source, "https://archive.org/search?query=x")).isEmpty();
        assertThat(match(source, "https://example.com/details/x")).isEmpty();
    }

    @Test
    void theRestartButtonIsForPlayersOnly() {
        new ExampleAddon().register(context);
        WindowAction restart = context.windowActions.getFirst();
        assertThat(restart.appliesTo(window(MediaKind.IMAGE, Optional.empty()))).isFalse();
        assertThat(ExampleAddon.restart(Optional.empty())).isFalse();
    }

    private static Optional<MediaRef> match(LinkSource source, String url) {
        return source.match(URI.create(url), url);
    }

    private static MediaWindow window(MediaKind kind, Optional<MediaHandle> handle) {
        return new MediaWindow() {
            @Override
            public MediaKind kind() {
                return kind;
            }

            @Override
            public Optional<String> url() {
                return Optional.empty();
            }

            @Override
            public Optional<MediaHandle> handle() {
                return handle;
            }
        };
    }

    /** Keeps everything registered, in lists. */
    private static final class Recording implements AddonContext {
        final List<LinkSource> sources = new ArrayList<>();
        final List<Resolver> resolvers = new ArrayList<>();
        final List<ThemeSpec> themes = new ArrayList<>();
        final List<WindowAction> windowActions = new ArrayList<>();
        final List<ShortcutSpec> shortcuts = new ArrayList<>();
        final List<KeyBindingSpec> keyBindings = new ArrayList<>();
        final List<SettingSpec<?>> settings = new ArrayList<>();
        final List<Interceptor> interceptors = new ArrayList<>();
        final List<Consumer<MediaPlayer>> ready = new ArrayList<>();

        private static <T> Registry<T> into(List<T> list) {
            return entry -> {
                list.add(entry);
                return entry;
            };
        }

        @Override
        public String addonId() {
            return ExampleAddon.ID;
        }

        @Override
        public Registry<LinkSource> sources() {
            return into(sources);
        }

        @Override
        public Registry<Resolver> resolvers() {
            return into(resolvers);
        }

        @Override
        public Registry<ThemeSpec> themes() {
            return into(themes);
        }

        @Override
        public Registry<WindowAction> windowActions() {
            return into(windowActions);
        }

        @Override
        public Registry<ShortcutSpec> shortcuts() {
            return into(shortcuts);
        }

        @Override
        public Registry<KeyBindingSpec> keyBindings() {
            return into(keyBindings);
        }

        @Override
        public Registry<SettingSpec<?>> settings() {
            return into(settings);
        }

        @Override
        public Registry<Interceptor> interceptors() {
            return into(interceptors);
        }

        @Override
        public EventSubscriptions events() {
            return new EventSubscriptions() {
                @Override
                public <E extends MediaEvent> Subscription on(Class<E> type, Consumer<? super E> listener) {
                    return () -> {};
                }
            };
        }

        @Override
        public void onReady(Consumer<MediaPlayer> callback) {
            ready.add(callback);
        }
    }
}
