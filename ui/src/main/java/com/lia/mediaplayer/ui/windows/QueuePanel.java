package com.lia.mediaplayer.ui.windows;

import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.library.Titles;
import com.lia.mediaplayer.core.text.Keys;
import com.lia.mediaplayer.ui.canvas.Canvas;
import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.icon.Glyphs;
import com.lia.mediaplayer.ui.theme.Role;
import com.lia.mediaplayer.ui.widget.Column;
import com.lia.mediaplayer.ui.widget.Constraints;
import com.lia.mediaplayer.ui.widget.IconButton;
import com.lia.mediaplayer.ui.widget.Label;
import com.lia.mediaplayer.ui.widget.ListView;
import com.lia.mediaplayer.ui.widget.RenderContext;
import com.lia.mediaplayer.ui.widget.Row;
import com.lia.mediaplayer.ui.widget.Size;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * What plays next, beside a video's picture or under the audio bar's controls: a header with the
 * count, then one row per waiting track with its title (looked up once its row shows) and a
 * button that drops it. A double click plays a track now; dragging one moves it in the queue.
 */
final class QueuePanel extends Column {

    static final int ROW_HEIGHT = 12;
    static final int VISIBLE_ROWS = 5;
    static final int HEIGHT = 11 + ROW_HEIGHT * VISIBLE_ROWS;
    /** The width it takes beside a picture, where it is as tall as the picture and controls. */
    static final int WIDTH = 120;

    private final PlaybackControl control;
    private final boolean beside;
    private final Label header;
    private final ListView<MediaRef> list;
    private int shownVersion = -1;

    /** @param beside whether it sits to the right of the player rather than under it */
    QueuePanel(PlaybackControl control, Function<String, Optional<String>> titles, boolean beside) {
        this.control = control;
        this.beside = beside;
        padding(0, 0);
        header = add(new Label(Text.literal(""), Role.TEXT_SUBTLE), 0, true);
        list = add(
                new ListView<MediaRef>(ROW_HEIGHT, () -> new Entry(control, titles))
                        .onActivate((index, press) -> {
                            if (press.clicks() == 2) {
                                control.jumpTo(index);
                            }
                        })
                        .reorderable(control::moveQueued, () -> true)
                        .whenEmpty(Text.translatable(Keys.QUEUE_EMPTY), Text.literal("")),
                1);
    }

    /** Reads the queue again if it changed since the last frame. */
    void update() {
        int version = control.queueVersion();
        if (version == shownVersion) {
            return;
        }
        shownVersion = version;
        List<MediaRef> upcoming = control.upcoming();
        list.setItems(upcoming);
        header.setText(Text.translatable(Keys.QUEUE_HEADER, upcoming.size()));
    }

    ListView<MediaRef> list() {
        return list;
    }

    @Override
    public Size measure(Constraints constraints) {
        Size size = super.measure(constraints);
        return beside ? new Size(WIDTH, size.height()) : new Size(size.width(), HEIGHT);
    }

    @Override
    public void render(Canvas canvas, RenderContext ctx) {
        canvas.fill(bounds(), ctx.color(Role.PANEL_BG));
        Rect b = bounds();
        // The border on the edge it shares with the player.
        if (beside) {
            canvas.fill(b.x(), b.y(), b.x() + 1, b.bottom(), ctx.color(Role.BORDER));
        } else {
            canvas.fill(b.x(), b.y(), b.right(), b.y() + 1, ctx.color(Role.BORDER));
        }
        super.render(canvas, ctx);
    }

    /** One waiting track. */
    private static final class Entry extends ListView.Row<MediaRef> {

        private final Function<String, Optional<String>> titles;
        private final Row row = new Row();
        private final Label number = new Label(Text.literal(""), Role.TEXT_SUBTLE);
        private final Label title = new Label(Text.literal(""), Role.TEXT);
        private int index;
        private String url = "";

        Entry(PlaybackControl control, Function<String, Optional<String>> titles) {
            this.titles = titles;
            row.padding(2, 0).gap(3);
            row.add(number);
            row.add(title, 1);
            row.add(new IconButton(Glyphs::close, () -> control.removeQueued(index))
                    .dangerous()
                    .tooltip(Text.translatable(Keys.QUEUE_REMOVE)));
            addChild(row);
        }

        @Override
        protected void bind(MediaRef item, int newIndex) {
            index = newIndex;
            url = item.url();
            number.setText(Text.literal((newIndex + 1) + "."));
            title.setText(Text.literal(Titles.shortForm(url)));
        }

        @Override
        public Size measure(Constraints constraints) {
            return new Size(constraints.maxWidth(), ROW_HEIGHT);
        }

        @Override
        public void layout(Rect newBounds, Constraints constraints) {
            super.layout(newBounds, constraints);
            row.layout(newBounds, constraints);
        }

        @Override
        public void render(Canvas canvas, RenderContext ctx) {
            // The title arrives once looked up; a row asks again each frame it shows.
            titles.apply(url).ifPresent(found -> title.setText(Text.literal(found)));
            super.render(canvas, ctx);
        }
    }
}
