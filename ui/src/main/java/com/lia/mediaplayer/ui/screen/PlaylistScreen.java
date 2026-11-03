package com.lia.mediaplayer.ui.screen;

import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.api.source.WebUrls;
import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.MediaPlayerCore;
import com.lia.mediaplayer.core.library.Library;
import com.lia.mediaplayer.core.library.M3u;
import com.lia.mediaplayer.core.library.Playlist;
import com.lia.mediaplayer.core.library.Titles;
import com.lia.mediaplayer.core.platform.TextMeasurer;
import com.lia.mediaplayer.core.resolve.ExpandedCollection;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * The playlists: the saved ones on the left (click to choose one, a box to create one, "In" to
 * make one from links on the clipboard), the chosen one on the right — rename it, search it,
 * add links, drag its entries into another order or remove them, play it in order or shuffled,
 * copy it out as m3u, delete it.
 *
 * <p>A link to a list (a YouTube playlist, a SoundCloud set) is expanded when added, so its
 * tracks join one by one. Titles are looked up only for the rows on screen. Dragging is off
 * while a search filters the entries: the rows shown are then not consecutive, and "drop it
 * here" would have no honest answer.
 */
public final class PlaylistScreen extends UiScreen {

    static final int ROW = 14;
    /** An entry's row: room for its thumbnail. */
    static final int ENTRY_ROW = Thumbnail.HEIGHT + 3;

    private static final long CONFIRM_NANOS = 3_000_000_000L;

    /** An entry of the chosen playlist, at its place in the whole playlist. */
    record Entry(int index, String url) {}

    private final MediaPlayerCore core;
    private final Library library;
    private final Titles titles;
    private final TextMeasurer measurer;
    private final LibraryActions actions;
    private final LongSupplier nanoTime;
    private final Label count;
    private final ListView<Playlist> playlists;
    private final TextField newName;
    private final Column chosen;
    private final Column nothing;
    private final Label nothingTitle;
    private final Label nothingHint;
    private final TextField name;
    private final TextField search;
    private final Label entriesCount;
    private final ListView<Entry> entries;
    private final TextField addLink;
    private final Label importing;
    private @Nullable String selectedId;
    private boolean loop;
    private int pendingImports;
    private long deleteArmedAt = Long.MIN_VALUE;
    private int shownVersion = -1;
    private @Nullable String shownFor;
    private String shownQuery = "";

    public PlaylistScreen(
            Supplier<Theme> theme,
            LongSupplier nanoTime,
            MediaPlayerCore core,
            LibraryActions actions,
            @Nullable String selectedId) {
        this(theme, nanoTime, core, actions, selectedId, new Column());
    }

    // The widgets are built here once and only shown, hidden or refilled afterwards, so an edit
    // never throws away the focus or what was half-typed.
    @SuppressWarnings("this-escape")
    private PlaylistScreen(
            Supplier<Theme> theme,
            LongSupplier nanoTime,
            MediaPlayerCore core,
            LibraryActions actions,
            @Nullable String selectedId,
            Column root) {
        super(Text.translatable(Keys.PLAYLISTS_TITLE), theme, nanoTime, root);
        this.core = core;
        this.library = core.library();
        this.titles = core.titles();
        this.measurer = core.platform().text();
        this.actions = actions;
        this.nanoTime = nanoTime;
        this.selectedId = selectedId;
        var clipboard = core.platform().clipboard();

        root.padding(8, 12).gap(6);
        root.add(
                new Label(Text.translatable(Keys.PLAYLISTS_TITLE), Role.TEXT)
                        .align(Label.Align.CENTER)
                        .shadow(true),
                0,
                true);
        var columns = root.add(new Row(), 1);
        columns.gap(10);

        // ---- Left: the playlists.
        var left = columns.add(new Column(), 1);
        left.gap(4);
        count = left.add(new Label(Text.literal(""), Role.TEXT_SUBTLE), 0, true);
        playlists = left.add(
                new ListView<Playlist>(ROW, PlaylistRow::new)
                        .selected(index -> playlistAt(index)
                                .map(p -> p.id().equals(this.selectedId))
                                .orElse(false))
                        .onActivate((index, press) -> playlistAt(index).ifPresent(p -> select(p.id())))
                        .whenEmpty(Text.translatable(Keys.PLAYLISTS_NONE), Text.literal("")),
                1);
        var create = left.add(new Row(), 0, true);
        create.gap(3);
        newName = create.add(
                new TextField(clipboard, Playlist.MAX_NAME)
                        .placeholder(Text.translatable(Keys.PLAYLISTS_NEW_NAME))
                        .onSubmit(text -> createPlaylist()),
                1);
        create.add(new TextButton(Text.literal("+"), this::createPlaylist)
                .tooltip(Text.translatable(Keys.PLAYLISTS_CREATE)));
        var leftButtons = left.add(new Row(), 0, true);
        leftButtons.gap(3);
        leftButtons.add(
                new TextButton(Text.translatable(Keys.PLAYLISTS_IMPORT), this::importClipboard)
                        .tooltip(Text.translatable(Keys.PLAYLISTS_IMPORT_TOOLTIP)),
                1);
        leftButtons.add(
                new TextButton(Text.translatable(Keys.PLAYLISTS_HISTORY), () -> actions.openHistory(this.selectedId)),
                1);

        // ---- Right: the chosen playlist, or what to do when there is none.
        var right = columns.add(new Column(), 2);
        chosen = right.add(new Column(), 1);
        chosen.gap(4);
        var renameRow = chosen.add(new Row(), 0, true);
        renameRow.gap(3);
        name = renameRow.add(new TextField(clipboard, Playlist.MAX_NAME).onSubmit(text -> rename()), 1);
        renameRow.add(new TextButton(Text.translatable(Keys.PLAYLISTS_RENAME), this::rename));
        search =
                chosen.add(new TextField(clipboard, 64).placeholder(Text.translatable(Keys.PLAYLISTS_SEARCH)), 0, true);
        entriesCount = chosen.add(new Label(Text.literal(""), Role.TEXT_SUBTLE), 0, true);
        entries = chosen.add(
                new ListView<Entry>(ENTRY_ROW, () -> new EntryRow(this))
                        .onActivate((index, press) -> {
                            if (press.clicks() == 2) {
                                entryAt(index).ifPresent(entry -> actions.play(entry.url()));
                            }
                        })
                        .reorderable(this::moveEntry, () -> search.text().isBlank()),
                1);
        var addRow = chosen.add(new Row(), 0, true);
        addRow.gap(3);
        addLink = addRow.add(
                new TextField(clipboard, 4096)
                        .placeholder(Text.translatable(Keys.PLAYLISTS_PASTE_LINK))
                        .onSubmit(text -> addLinks()),
                1);
        addRow.add(new TextButton(Text.translatable(Keys.PLAYLISTS_ADD), this::addLinks));
        var playRow = chosen.add(new Row(), 0, true);
        playRow.gap(3);
        playRow.add(
                new TextButton(Text.translatable(Keys.PLAYLISTS_PLAY), () -> play(false)).enabledWhen(this::hasEntries),
                1);
        playRow.add(
                new TextButton(Text.translatable(Keys.PLAYLISTS_SHUFFLE), () -> play(true))
                        .enabledWhen(this::hasEntries),
                1);
        playRow.add(
                new TextButton(
                                () -> Text.translatable(loop ? Keys.PLAYLISTS_LOOP_ON : Keys.PLAYLISTS_LOOP_OFF),
                                press -> loop = !loop)
                        .activeWhen(() -> loop)
                        .tooltip(Text.translatable(Keys.PLAYLISTS_LOOP_TOOLTIP)),
                1);
        // Equal shares, so every button keeps a share of the row however long its label runs.
        playRow.add(
                new TextButton(Text.translatable(Keys.PLAYLISTS_EXPORT), this::exportClipboard)
                        .tooltip(Text.translatable(Keys.PLAYLISTS_EXPORT_TOOLTIP)),
                1);
        playRow.add(
                new TextButton(
                        () -> Text.translatable(deleteArmed() ? Keys.PLAYLISTS_DELETE_CONFIRM : Keys.PLAYLISTS_DELETE),
                        press -> deleteSelected()),
                1);
        importing = chosen.add(new Label(Text.translatable(Keys.PLAYLISTS_IMPORTING), Role.TEXT_SUBTLE), 0, true);

        nothing = right.add(new Column(), 1);
        nothing.add(new Spacer(), 1);
        nothingTitle = nothing.add(new Label(Text.literal(""), Role.TEXT_SUBTLE).align(Label.Align.CENTER), 0, true);
        nothingHint = nothing.add(new Label(Text.literal(""), Role.TEXT_DIM).align(Label.Align.CENTER), 0, true);
        nothing.add(new Spacer(), 1);

        var footer = root.add(new Row(), 0, true);
        footer.add(new Spacer(), 1);
        footer.add(new TextButton(Text.translatable(Keys.DONE), actions::close).minWidth(60));
        refresh();
    }

    // ---- State --------------------------------------------------------------

    public Optional<Playlist> selected() {
        String id = selectedId;
        return id == null ? Optional.empty() : library.playlist(id);
    }

    void select(String id) {
        selectedId = id;
        search.setText("");
        selected().ifPresent(p -> name.setText(p.name()));
        deleteArmedAt = Long.MIN_VALUE;
        refresh();
    }

    private Optional<Playlist> playlistAt(int index) {
        List<Playlist> items = playlists.items();
        return index >= 0 && index < items.size() ? Optional.of(items.get(index)) : Optional.empty();
    }

    private Optional<Entry> entryAt(int index) {
        List<Entry> items = entries.items();
        return index >= 0 && index < items.size() ? Optional.of(items.get(index)) : Optional.empty();
    }

    private boolean hasEntries() {
        return selected().map(p -> p.size() > 0).orElse(false);
    }

    @Override
    protected void beforeRender() {
        String query = search.text().strip().toLowerCase(Locale.ROOT);
        boolean stale = library.version() != shownVersion
                || !Objects.equals(selectedId, shownFor)
                || !query.equals(shownQuery)
                // While a search filters by title, titles arriving change what matches.
                || !query.isEmpty();
        if (stale) {
            refresh();
        }
        importing.setVisible(pendingImports > 0);
    }

    private void refresh() {
        shownVersion = library.version();
        shownFor = selectedId;
        shownQuery = search.text().strip().toLowerCase(Locale.ROOT);
        List<Playlist> all = library.playlists();
        playlists.setItems(all);
        count.setText(Text.translatable(Keys.PLAYLISTS_COUNT, all.size()));
        Optional<Playlist> playlist = selected();
        if (playlist.isEmpty()) {
            selectedId = null;
        }
        chosen.setVisible(playlist.isPresent());
        nothing.setVisible(playlist.isEmpty());
        nothingTitle.setText(Text.translatable(all.isEmpty() ? Keys.PLAYLISTS_NONE : Keys.PLAYLISTS_SELECT));
        nothingHint.setText(Text.translatable(all.isEmpty() ? Keys.PLAYLISTS_NONE_HINT : Keys.PLAYLISTS_SELECT_HINT));
        if (playlist.isEmpty()) {
            entries.setItems(List.of());
            return;
        }
        var shown = new ArrayList<Entry>();
        List<String> urls = playlist.get().urls();
        for (int i = 0; i < urls.size(); i++) {
            if (shownQuery.isEmpty()
                    || shownTitle(urls.get(i)).toLowerCase(Locale.ROOT).contains(shownQuery)) {
                shown.add(new Entry(i, urls.get(i)));
            }
        }
        entries.setItems(shown);
        entriesCount.setText(
                shownQuery.isEmpty()
                        ? Text.translatable(Keys.PLAYLISTS_ENTRIES, urls.size())
                        : Text.translatable(Keys.PLAYLISTS_ENTRIES_FILTERED, shown.size(), urls.size()));
        if (urls.isEmpty()) {
            entries.whenEmpty(Text.translatable(Keys.PLAYLISTS_EMPTY), Text.translatable(Keys.PLAYLISTS_EMPTY_HINT));
        } else {
            entries.whenEmpty(
                    Text.translatable(Keys.PLAYLISTS_NO_MATCH), Text.translatable(Keys.PLAYLISTS_NO_MATCH_HINT));
        }
    }

    /** What an entry shows as: its title once known, its link until then. */
    String shownTitle(String url) {
        return titles.peek(url).orElseGet(() -> Titles.shortForm(url));
    }

    // ---- Actions ------------------------------------------------------------

    void createPlaylist() {
        String typed = newName.text().strip();
        Playlist created = library.create(
                typed.isEmpty() ? measurer.resolve(Text.translatable(Keys.PLAYLISTS_DEFAULT_NAME)) : typed);
        newName.setText("");
        select(created.id());
    }

    private void rename() {
        selected().ifPresent(p -> library.rename(p.id(), name.text()));
        selected().ifPresent(p -> name.setText(p.name()));
    }

    /** Adds the links in the box, separated by spaces. Lists are expanded, their tracks added once known. */
    void addLinks() {
        Optional<Playlist> target = selected();
        if (target.isEmpty()) {
            return;
        }
        String id = target.get().id();
        var plain = new ArrayList<String>();
        for (String word :
                Arrays.stream(addLink.text().strip().split("\\s+", -1)).toList()) {
            Optional<MediaRef> ref = core.sources().match(word);
            if (ref.isPresent() && ref.get().kind() == MediaKind.COLLECTION) {
                expand(id, ref.get());
            } else if (WebUrls.isWebUrl(word)) {
                plain.add(word);
            }
        }
        library.add(id, plain);
        addLink.setText("");
    }

    private void expand(String id, MediaRef collection) {
        pendingImports++;
        CompletableFuture<ExpandedCollection> expansion = core.collections().expand(collection);
        var unused = expansion.whenCompleteAsync(
                (expanded, error) -> {
                    pendingImports--;
                    if (error == null) {
                        library.add(
                                id,
                                expanded.entries().stream().map(MediaRef::url).toList());
                    }
                },
                core.platform().clientThread());
    }

    void moveEntry(int from, int insertBefore) {
        List<Entry> shown = entries.items();
        selected().ifPresent(p -> {
            if (from >= 0 && from < shown.size()) {
                int before =
                        insertBefore < shown.size() ? shown.get(insertBefore).index() : p.size();
                library.moveEntry(p.id(), shown.get(from).index(), before);
            }
        });
    }

    void removeEntry(int index) {
        selected().ifPresent(p -> library.removeEntry(p.id(), index));
    }

    private void play(boolean shuffle) {
        selected().ifPresent(p -> actions.playAll(p.urls(), shuffle, loop));
    }

    /** Makes a playlist of the links on the clipboard: an m3u file, or links one per line. */
    void importClipboard() {
        List<String> urls = M3u.parse(core.platform().clipboard().get());
        if (urls.isEmpty()) {
            return;
        }
        Playlist created = library.create(measurer.resolve(Text.translatable(Keys.PLAYLISTS_IMPORTED_NAME)), urls);
        select(created.id());
    }

    void exportClipboard() {
        selected()
                .ifPresent(p -> core.platform()
                        .clipboard()
                        .set(M3u.export(p, url -> titles.peek(url).orElse(null))));
    }

    /** The first click arms the button; a second within three seconds deletes. */
    void deleteSelected() {
        if (!deleteArmed()) {
            deleteArmedAt = nanoTime.getAsLong();
            return;
        }
        selected().ifPresent(p -> library.delete(p.id()));
        selectedId = null;
        deleteArmedAt = Long.MIN_VALUE;
        refresh();
    }

    private boolean deleteArmed() {
        return deleteArmedAt != Long.MIN_VALUE && nanoTime.getAsLong() - deleteArmedAt < CONFIRM_NANOS;
    }

    // The parts, for tests.

    TextField newName() {
        return newName;
    }

    TextField addLink() {
        return addLink;
    }

    TextField search() {
        return search;
    }

    ListView<Entry> entries() {
        return entries;
    }

    ListView<Playlist> playlists() {
        return playlists;
    }

    void setLoop(boolean on) {
        loop = on;
    }

    // ---- Rows ---------------------------------------------------------------

    /** A saved playlist: its name and how many entries it has. */
    private static final class PlaylistRow extends Rows<Playlist> {

        private final Label name = new Label(Text.literal(""), Role.TEXT);
        private final Label size = new Label(Text.literal(""), Role.TEXT_SUBTLE);

        PlaylistRow() {
            super(ROW);
            row.add(name, 1);
            row.add(size);
        }

        @Override
        protected void bind(Playlist item, int index) {
            name.setText(Text.literal(item.name()));
            size.setText(Text.literal(Integer.toString(item.size())));
        }
    }

    /** An entry of the chosen playlist: its place, its thumbnail, its title and a button that removes it. */
    private static final class EntryRow extends Rows<Entry> {

        private final PlaylistScreen screen;
        private final Label number = new Label(Text.literal(""), Role.TEXT_SUBTLE);
        private final Thumbnail thumbnail;
        private final Label title = new Label(Text.literal(""), Role.TEXT);
        private int index;
        private String url = "";

        EntryRow(PlaylistScreen screen) {
            super(ENTRY_ROW);
            this.screen = screen;
            thumbnail = new Thumbnail(screen.core.thumbnails()::get);
            row.add(number);
            row.add(thumbnail);
            row.add(title, 1);
            row.add(new IconButton(Glyphs::close, () -> screen.removeEntry(index))
                    .dangerous()
                    .tooltip(Text.translatable(Keys.PLAYLISTS_REMOVE)));
        }

        @Override
        protected void bind(Entry item, int position) {
            index = item.index();
            url = item.url();
            number.setText(Text.literal((item.index() + 1) + "."));
            thumbnail.show(url);
            title.setText(Text.literal(Titles.shortForm(url)));
        }

        @Override
        public void render(Canvas canvas, RenderContext ctx) {
            screen.titles.get(url).ifPresent(found -> title.setText(Text.literal(found)));
            super.render(canvas, ctx);
        }
    }
}
