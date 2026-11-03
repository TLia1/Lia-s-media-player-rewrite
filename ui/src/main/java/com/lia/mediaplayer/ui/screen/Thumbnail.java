package com.lia.mediaplayer.ui.screen;

import com.lia.mediaplayer.core.image.ImageTextures;
import com.lia.mediaplayer.core.platform.TextureRef;
import com.lia.mediaplayer.ui.canvas.Canvas;
import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.theme.Role;
import com.lia.mediaplayer.ui.widget.Constraints;
import com.lia.mediaplayer.ui.widget.RenderContext;
import com.lia.mediaplayer.ui.widget.Size;
import com.lia.mediaplayer.ui.widget.Widget;
import java.util.Optional;
import java.util.function.Function;

/**
 * The small picture at the start of a list row: the link's thumbnail fitted in a 16:9 box, or
 * the empty box while it loads or when the link has none. Asks for the picture only when drawn,
 * so only the rows on screen load theirs.
 */
final class Thumbnail extends Widget {

    static final int WIDTH = 48;
    static final int HEIGHT = 27;

    private final Function<String, Optional<ImageTextures>> thumbnails;
    private String url = "";

    /** @param thumbnails a link's thumbnail if it is ready, starting to load it if not */
    Thumbnail(Function<String, Optional<ImageTextures>> thumbnails) {
        this.thumbnails = thumbnails;
    }

    void show(String newUrl) {
        url = newUrl;
    }

    @Override
    public Size measure(Constraints constraints) {
        return new Size(WIDTH, HEIGHT);
    }

    @Override
    public void render(Canvas canvas, RenderContext ctx) {
        Rect b = bounds();
        Rect box = new Rect(b.x(), b.centerY() - HEIGHT / 2, WIDTH, HEIGHT);
        canvas.fill(box, ctx.color(Role.PLACEHOLDER));
        if (url.isEmpty()) {
            return;
        }
        thumbnails.apply(url).ifPresent(image -> {
            TextureRef texture = image.frameAt(ctx.nanos());
            if (texture != null) {
                Rect picture = box.fit(image.width(), image.height());
                canvas.texture(texture, picture.x(), picture.y(), picture.width(), picture.height());
            }
        });
    }
}
