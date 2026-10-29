package com.lia.mediaplayer.ui.screen;

import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.library.HistoryEntry;
import com.lia.mediaplayer.core.library.Library;
import com.lia.mediaplayer.core.library.Playlist;
import com.lia.mediaplayer.core.library.Titles;
import com.lia.mediaplayer.core.platform.Platform;
import com.lia.mediaplayer.core.text.Keys;
import com.lia.mediaplayer.ui.canvas.Canvas;
import com.lia.mediaplayer.ui.icon.Glyphs;
import com.lia.mediaplayer.ui.theme.Role;
import com.lia.mediaplayer.ui.theme.Theme;
import com.lia.mediaplayer.ui.widget.Column;
import com.lia.mediaplayer.ui.widget.IconButton;
import com.lia.mediaplayer.ui.widget.Label;
import com.lia.mediaplayer.ui.widget.ListView;
import com.lia.mediaplayer.ui.widget.RenderContext;
import com.lia.mediaplayer.ui.widget.Row;
import com.lia.mediaplayer.ui.widget.Spacer;
import com.lia.mediaplayer.ui.widget.TextButton;
import com.lia.mediaplayer.ui.widget.TextField;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Everything played, most recent first, with the hearts: search it, show only the favourites,
 * play an entry again (double click or its button), add it to the playlist that was chosen
 * when this screen opened, take it out, or clear the lot — favourites excepted, since they
 * were kept on purpose.
 */
public final class HistoryScreen extends UiScreen {

    static final int ROW = 14;

    private final Library library;
    private final Titles titles;
    private final LibraryActions actions;
    private final @Nullable String targetId;
    private final TextField search;
    private final Label count;
    private final ListView<HistoryEntry> list;
    private boolean favoritesOnly;
    private int shownVersion = -1;
    private String shownQuery = "";
    private boolean shownFavorites;

    public HistoryScreen(
            Supplier<Theme> theme,
            LongSupplier nanoTime,
            Library library,
            Titles titles,
            Platform.Clipboard clipboard,
            LibraryActions actions,
            @Nullable String targetId) {
        this(theme, nanoTime, library, titles, clipboard, actions, targetId, new Column());
    }

    @SuppressWarnings("this-escape")
    private HistoryScreen(
            Supplier<Theme> theme,
            LongSupplier nanoTime,
            Library library,
            Titles titles,
            Platform.Clipboard clipboard,
            LibraryActions actions,
            @Nullable String targetId,
            Column root) {
        super(Text.translatable(Keys.HISTORY_TITLE), theme, nanoTime, root);
        this.library = library;
        this.titles = titles;
        this.actions = actions;
        this.targetId = targetId;
        root.padding(8, 12).gap(6);
        root.add(
                new Label(Text.translatable(Keys.HISTORY_TITLE), Role.TEXT)
                        .align(Label.Align.CENTER)
                        .shadow(true),
                0,
                true);
        var tools = root.add(new Row(), 0, true);
        tools.gap(4);
        search = tools.add(new TextField(clipboard, 64).placeholder(Text.translatable(Keys.HISTORY_SEARCH)), 1);
        tools.add(new TextButton(
                        () -> Text.translatable(favoritesOnly ? Keys.HISTORY_FAVORITES_ON : Keys.HISTORY_FAVORITES_OFF),
                        press -> favoritesOnly = !favoritesOnly)
                .activeWhen(() -> favoritesOnly)
                .tooltip(Text.translatable(Keys.HISTORY_FAVORITES_TOOLTIP)));
        tools.add(new TextButton(Text.translatable(Keys.HISTORY_CLEAR), library::clearHistory)
                .tooltip(Text.translatable(Keys.HISTORY_CLEAR_TOOLTIP)));
        count = root.add(new Label(Text.literal(""), Role.TEXT_SUBTLE), 0, true);
        list = root.add(
                new ListView<HistoryEntry>(ROW, () -> new EntryRow(this))
                        .onActivate((index, press) -> {
                            if (press.clicks() == 2) {
                                entryAt(index).ifPresent(entry -> actions.play(entry.url()));
                            }
                        })
                        .whenEmpty(Text.translatable(Keys.HISTORY_EMPTY), Text.translatable(Keys.HISTORY_EMPTY_HINT)),
                1);
        var footer = root.add(new Row(), 0, true);
        footer.add(new Spacer(), 1);
        footer.add(new TextButton(Text.translatable(Keys.BACK), actions::close).minWidth(60));
        refresh();
    }

    private Optional<HistoryEntry> entryAt(int index) {
        List<HistoryEntry> items = list.items();
        return index >= 0 && index < items.size() ? Optional.of(items.get(index)) : Optional.empty();
    }

    /** The playlist the "add" buttons add to, if there is one. */
    Optional<Playlist> target() {
        String id = targetId;
        return id == null ? Optional.empty() : library.playlist(id);
    }

    @Override
    protected void beforeRender() {
        String query = search.text().strip().toLowerCase(Locale.ROOT);
        if (library.version() != shownVersion
                || !query.equals(shownQuery)
                || favoritesOnly != shownFavorites
                || !query.isEmpty()) {
            refresh();
        }
    }

    private void refresh() {
        shownVersion = library.version();
        shownQuery = search.text().strip().toLowerCase(Locale.ROOT);
        shownFavorites = favoritesOnly;
        List<HistoryEntry> shown = library.history().stream()
                .filter(entry -> !favoritesOnly || entry.favorite())
                .filter(entry -> shownQuery.isEmpty()
                        || titles.peek(entry.url())
                                .orElse(entry.url())
                                .toLowerCase(Locale.ROOT)
                                .contains(shownQuery))
                .toList();
        list.setItems(shown);
        count.setText(Text.translatable(Keys.HISTORY_COUNT, shown.size()));
    }

    void setFavoritesOnly(boolean on) {
        favoritesOnly = on;
    }

    TextField search() {
        return search;
    }

    ListView<HistoryEntry> list() {
        return list;
    }

    static Text kindTag(HistoryEntry entry) {
        return Text.translatable(
                switch (entry.kind()) {
                    case IMAGE -> Keys.HISTORY_KIND_IMAGE;
                    case AUDIO -> Keys.HISTORY_KIND_AUDIO;
                    case VIDEO, COLLECTION -> Keys.HISTORY_KIND_VIDEO;
                });
    }

    /** One entry: what it is, its title, and its buttons. */
    private static final class EntryRow extends Rows<HistoryEntry> {

        private final HistoryScreen screen;
        private final Label kind = new Label(Text.literal(""), Role.TEXT_SUBTLE);
        private final Label title = new Label(Text.literal(""), Role.TEXT);
        private @Nullable HistoryEntry entry;

        EntryRow(HistoryScreen screen) {
            super(ROW);
            this.screen = screen;
            row.add(kind);
            row.add(title, 1);
            row.add(new IconButton(Glyphs::play, () -> current().ifPresent(e -> screen.actions.play(e.url())))
                    .tooltip(Text.translatable(Keys.HISTORY_PLAY)));
            row.add(new IconButton(
                            (c, x, y, color) -> Glyphs.heart(
                                    c,
                                    x,
                                    y,
                                    current().map(HistoryEntry::favorite).orElse(false),
                                    color),
                            () -> current().ifPresent(e -> screen.library.toggleFavorite(e.url(), e.kind())))
                    .activeWhen(() -> current().map(HistoryEntry::favorite).orElse(false))
                    .tooltip(() -> Text.translatable(
                            current().map(HistoryEntry::favorite).orElse(false) ? Keys.UNFAVORITE : Keys.FAVORITE)));
            row.add(new IconButton(Glyphs::plus, this::addToTarget)
                    .enabledWhen(() -> screen.target().isPresent())
                    .tooltip(() -> screen.target()
                            .map(p -> Text.translatable(Keys.HISTORY_ADD, p.name()))
                            .orElse(Text.translatable(Keys.HISTORY_ADD_NONE))));
            row.add(new IconButton(
                            Glyphs::close, () -> current().ifPresent(e -> screen.library.removeFromHistory(e.url())))
                    .dangerous()
                    .tooltip(Text.translatable(Keys.HISTORY_REMOVE)));
        }

        private void addToTarget() {
            HistoryEntry shown = entry;
            Optional<Playlist> target = screen.target();
            if (shown != null && target.isPresent()) {
                screen.library.add(target.get().id(), List.of(shown.url()));
            }
        }

        private Optional<HistoryEntry> current() {
            return Optional.ofNullable(entry);
        }

        @Override
        protected void bind(HistoryEntry item, int index) {
            entry = item;
            kind.setText(kindTag(item));
            title.setText(Text.literal(Titles.shortForm(item.url())));
        }

        @Override
        public void render(Canvas canvas, RenderContext ctx) {
            HistoryEntry shown = entry;
            if (shown != null) {
                screen.titles.get(shown.url()).ifPresent(found -> title.setText(Text.literal(found)));
            }
            super.render(canvas, ctx);
        }
    }
}
