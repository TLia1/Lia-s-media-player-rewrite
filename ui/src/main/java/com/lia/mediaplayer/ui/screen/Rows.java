package com.lia.mediaplayer.ui.screen;

import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.widget.Constraints;
import com.lia.mediaplayer.ui.widget.ListView;
import com.lia.mediaplayer.ui.widget.Row;
import com.lia.mediaplayer.ui.widget.Size;

/** A list row whose widgets sit in one {@link Row} filling it. */
abstract class Rows<T> extends ListView.Row<T> {

    protected final Row row = new Row();
    private final int height;

    // The row is a child from the start, so the subclass only adds to it.
    @SuppressWarnings("this-escape")
    Rows(int height) {
        this.height = height;
        row.padding(3, 0).gap(3);
        addChild(row);
    }

    @Override
    public Size measure(Constraints constraints) {
        return new Size(constraints.maxWidth(), height);
    }

    @Override
    public void layout(Rect newBounds, Constraints constraints) {
        super.layout(newBounds, constraints);
        row.layout(newBounds, constraints);
    }
}
