package com.lia.mediaplayer.ui.widget;

import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.platform.Platform;
import com.lia.mediaplayer.core.platform.TextMeasurer;
import com.lia.mediaplayer.ui.canvas.Canvas;
import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.input.InputEvent;
import com.lia.mediaplayer.ui.input.Key;
import com.lia.mediaplayer.ui.input.Mods;
import com.lia.mediaplayer.ui.theme.Role;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * One line of editable text. Clicking it takes the keyboard focus; it then takes characters,
 * moves its cursor with the arrows, Home and End (by word with Ctrl, or Cmd on macOS),
 * selects with Shift, a drag, a double click (a word) or a triple click (everything), and
 * knows Ctrl+A, C, X and V. Enter submits. Escape is left to the screen, which closes.
 *
 * <p>The text scrolls sideways to keep the cursor in view. A grey placeholder says what to
 * type while it is empty.
 */
public final class TextField extends Widget {

    static final int PAD = 3;
    private static final long BLINK_NANOS = 500_000_000L;

    private final Platform.Clipboard clipboard;
    private final int maxLength;
    private String text = "";
    private int cursor;
    private int anchor;
    private int scroll;
    private Text placeholder = Text.literal("");
    private Consumer<String> onChange = value -> {};
    private Consumer<String> onSubmit = value -> {};
    private @Nullable TextMeasurer measurer;
    private boolean focused;
    private boolean selecting;

    public TextField(Platform.Clipboard clipboard, int maxLength) {
        this.clipboard = clipboard;
        this.maxLength = maxLength;
    }

    public TextField placeholder(Text hint) {
        placeholder = hint;
        return this;
    }

    /** Called with the new text after every edit the player makes. */
    public TextField onChange(Consumer<String> listener) {
        onChange = listener;
        return this;
    }

    /** Called with the text when Enter is pressed. */
    public TextField onSubmit(Consumer<String> listener) {
        onSubmit = listener;
        return this;
    }

    public String text() {
        return text;
    }

    /** Replaces the text, cursor at the end, without calling the change listener. */
    public void setText(String newText) {
        text = clip(newText);
        cursor = text.length();
        anchor = cursor;
    }

    public boolean isFocused() {
        return focused;
    }

    public int cursor() {
        return cursor;
    }

    /** The selected text, empty when nothing is. */
    public String selection() {
        return text.substring(Math.min(cursor, anchor), Math.max(cursor, anchor));
    }

    @Override
    public Size measure(Constraints constraints) {
        measurer = constraints.text();
        return new Size(60, constraints.text().lineHeight() + 2 * PAD);
    }

    // ---- Drawing ------------------------------------------------------------

    @Override
    public void render(Canvas canvas, RenderContext ctx) {
        Rect b = bounds();
        TextMeasurer font = canvas.measurer();
        measurer = font;
        canvas.fill(b, ctx.color(Role.FIELD_BG));
        canvas.outline(b, ctx.color(focused ? Role.BORDER_FOCUSED : Role.BORDER));
        Rect inner = b.inset(PAD, 1, PAD, 1);
        int textY = b.y() + (b.height() - font.lineHeight() + 1) / 2;
        keepCursorVisible(font, inner.width());
        canvas.pushClip(inner);
        if (text.isEmpty() && !focused) {
            canvas.text(placeholder, inner.x(), textY, ctx.color(Role.TEXT_DIM), false);
        }
        int x = inner.x() - scroll;
        if (cursor != anchor) {
            int from = x + font.width(text.substring(0, Math.min(cursor, anchor)));
            int to = x + font.width(text.substring(0, Math.max(cursor, anchor)));
            canvas.fill(from, textY - 1, to, textY + font.lineHeight(), ctx.color(Role.ROW_SELECTED));
        }
        canvas.text(Text.literal(text), x, textY, ctx.color(Role.TEXT), false);
        if (focused && (ctx.nanos() / BLINK_NANOS) % 2 == 0) {
            int cx = x + font.width(text.substring(0, cursor));
            canvas.fill(cx, textY - 1, cx + 1, textY + font.lineHeight(), ctx.color(Role.TEXT));
        }
        canvas.popClip();
    }

    private void keepCursorVisible(TextMeasurer font, int width) {
        int cursorX = font.width(text.substring(0, cursor));
        if (cursorX - scroll > width - 1) {
            scroll = cursorX - width + 1;
        } else if (cursorX < scroll) {
            scroll = cursorX;
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, font.width(text) - width + 1)));
    }

    // ---- Input --------------------------------------------------------------

    @Override
    public boolean onInput(InputEvent event, InputContext ctx) {
        focused = ctx.isFocused(this);
        return switch (event) {
            case InputEvent.PointerDown down
            when down.button() == InputEvent.PRIMARY_BUTTON -> {
                ctx.focus(this);
                focused = true;
                int at = indexAt(down.x());
                switch (Math.min(3, down.clicks())) {
                    case 2 -> selectWordAt(at);
                    case 3 -> select(0, text.length());
                    default -> {
                        cursor = at;
                        if (!down.mods().shift()) {
                            anchor = at;
                        }
                        selecting = true;
                        ctx.capture(this);
                    }
                }
                yield true;
            }
            case InputEvent.PointerDrag drag
            when selecting -> {
                cursor = indexAt(drag.x());
                yield true;
            }
            case InputEvent.PointerUp up
            when selecting -> {
                selecting = false;
                ctx.releaseCapture();
                yield true;
            }
            case InputEvent.KeyDown key when focused -> key(key.key(), key.mods());
            case InputEvent.CharTyped typed
            when focused -> {
                if (typed.codePoint() >= 32 && typed.codePoint() != 127) {
                    insert(new String(Character.toChars(typed.codePoint())));
                }
                yield true;
            }
            default -> false;
        };
    }

    /** Lets go of the keyboard: something else was clicked. */
    public void blur() {
        focused = false;
        selecting = false;
    }

    private boolean key(Key key, Mods mods) {
        boolean word = mods.primary();
        switch (key) {
            case LEFT ->
                moveTo(
                        word ? wordStart(cursor) : cursor > 0 && !hasSelection(mods) ? cursor - 1 : leftEdge(mods),
                        mods);
            case RIGHT ->
                moveTo(
                        word
                                ? wordEnd(cursor)
                                : cursor < text.length() && !hasSelection(mods) ? cursor + 1 : rightEdge(mods),
                        mods);
            case HOME -> moveTo(0, mods);
            case END -> moveTo(text.length(), mods);
            case BACKSPACE -> {
                if (cursor == anchor) {
                    anchor = word ? wordStart(cursor) : Math.max(0, cursor - 1);
                }
                insert("");
            }
            case DELETE -> {
                if (cursor == anchor) {
                    anchor = word ? wordEnd(cursor) : Math.min(text.length(), cursor + 1);
                }
                insert("");
            }
            case ENTER -> onSubmit.accept(text);
            case A -> {
                if (!mods.primary()) {
                    return false;
                }
                select(0, text.length());
            }
            case C -> {
                if (!mods.primary()) {
                    return false;
                }
                copy();
            }
            case X -> {
                if (!mods.primary()) {
                    return false;
                }
                copy();
                insert("");
            }
            case V -> {
                if (!mods.primary()) {
                    return false;
                }
                insert(clipboard.get().replace('\n', ' ').replace('\r', ' '));
            }
            default -> {
                // Everything else is the screen's (Escape, Tab) or arrives as a character.
                return false;
            }
        }
        return true;
    }

    private boolean hasSelection(Mods mods) {
        return cursor != anchor && !mods.shift();
    }

    private int leftEdge(Mods mods) {
        return hasSelection(mods) ? Math.min(cursor, anchor) : cursor;
    }

    private int rightEdge(Mods mods) {
        return hasSelection(mods) ? Math.max(cursor, anchor) : cursor;
    }

    private void moveTo(int index, Mods mods) {
        cursor = Math.max(0, Math.min(text.length(), index));
        if (!mods.shift()) {
            anchor = cursor;
        }
    }

    private void select(int from, int to) {
        anchor = from;
        cursor = to;
    }

    private void copy() {
        String selected = selection();
        if (!selected.isEmpty()) {
            clipboard.set(selected);
        }
    }

    /** Replaces the selection (or nothing, at the cursor) with {@code inserted}. */
    private void insert(String inserted) {
        int from = Math.min(cursor, anchor);
        int to = Math.max(cursor, anchor);
        String room = inserted.substring(0, Math.min(inserted.length(), maxLength - (text.length() - (to - from))));
        String changed = text.substring(0, from) + room + text.substring(to);
        cursor = from + room.length();
        anchor = cursor;
        if (!changed.equals(text)) {
            text = changed;
            onChange.accept(text);
        }
    }

    private void selectWordAt(int at) {
        select(wordStart(Math.min(text.length(), at + 1)), wordEnd(at));
    }

    private int wordStart(int from) {
        int i = from;
        while (i > 0 && Character.isWhitespace(text.charAt(i - 1))) {
            i--;
        }
        while (i > 0 && !Character.isWhitespace(text.charAt(i - 1))) {
            i--;
        }
        return i;
    }

    private int wordEnd(int from) {
        int i = from;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        while (i < text.length() && !Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    /** The gap between characters nearest to {@code x}. */
    private int indexAt(double x) {
        TextMeasurer font = measurer;
        if (font == null) {
            return text.length();
        }
        double local = x - bounds().x() - PAD + scroll;
        int best = 0;
        for (int i = 1; i <= text.length(); i++) {
            int left = font.width(text.substring(0, i - 1));
            int right = font.width(text.substring(0, i));
            if (local >= (left + right) / 2.0) {
                best = i;
            } else {
                break;
            }
        }
        return best;
    }

    private String clip(String value) {
        String oneLine = value.replace('\n', ' ').replace('\r', ' ');
        return oneLine.length() > maxLength ? oneLine.substring(0, maxLength) : oneLine;
    }
}
