package com.lia.mediaplayer.ui.testing;

import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.platform.TextMeasurer;
import com.lia.mediaplayer.core.platform.TextureRef;
import com.lia.mediaplayer.core.testing.FakeText;
import com.lia.mediaplayer.ui.canvas.Canvas;
import com.lia.mediaplayer.ui.canvas.Rect;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A canvas that draws nothing and remembers every call, in order, with the layer it was made
 * on. Tests assert on what was drawn where. Text is measured with {@link FakeText}.
 */
public final class RecordingCanvas implements Canvas {

    public sealed interface Call {
        int layer();
    }

    public record Fill(Rect rect, int argb, int layer) implements Call {}

    public record TextureCall(TextureRef texture, Rect rect, int layer) implements Call {}

    public record TextCall(String text, int x, int y, int argb, boolean shadow, int layer) implements Call {}

    public record Tooltip(List<String> lines, int x, int y, int layer) implements Call {}

    private final int width;
    private final int height;
    private final FakeText measurer = new FakeText();
    private final List<Call> calls = new ArrayList<>();
    private int layer;
    private int clipDepth;
    private int maxClipDepth;

    public RecordingCanvas(int width, int height) {
        this.width = width;
        this.height = height;
    }

    /** Forgets what was drawn: the start of a new frame. */
    private double pixelScale = 1;

    /** Draws as a screen with {@code scale} real pixels per GUI pixel would. */
    public RecordingCanvas withPixelScale(double scale) {
        pixelScale = scale;
        return this;
    }

    @Override
    public double pixelScale() {
        return pixelScale;
    }

    public RecordingCanvas clear() {
        calls.clear();
        layer = 0;
        clipDepth = 0;
        return this;
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public void fill(int x0, int y0, int x1, int y1, int argb) {
        if (x1 > x0 && y1 > y0) {
            calls.add(new Fill(Rect.ofEdges(x0, y0, x1, y1), argb, layer));
        }
    }

    @Override
    public void texture(TextureRef texture, int x, int y, int w, int h) {
        calls.add(new TextureCall(texture, new Rect(x, y, w, h), layer));
    }

    @Override
    public void text(Text text, int x, int y, int argb, boolean shadow) {
        calls.add(new TextCall(measurer.resolve(text), x, y, argb, shadow, layer));
    }

    @Override
    public TextMeasurer measurer() {
        return measurer;
    }

    @Override
    public void pushClip(int x0, int y0, int x1, int y1) {
        clipDepth++;
        maxClipDepth = Math.max(maxClipDepth, clipDepth);
    }

    @Override
    public void popClip() {
        clipDepth--;
    }

    @Override
    public void nextLayer() {
        layer++;
    }

    @Override
    public void tooltip(List<Text> lines, int x, int y) {
        calls.add(new Tooltip(lines.stream().map(measurer::resolve).toList(), x, y, layer));
    }

    public List<Call> calls() {
        return List.copyOf(calls);
    }

    public List<Fill> fills() {
        return calls.stream()
                .filter(Fill.class::isInstance)
                .map(Fill.class::cast)
                .toList();
    }

    public List<TextCall> texts() {
        return calls.stream()
                .filter(TextCall.class::isInstance)
                .map(TextCall.class::cast)
                .toList();
    }

    public List<String> strings() {
        return texts().stream().map(TextCall::text).toList();
    }

    public List<TextureCall> textures() {
        return calls.stream()
                .filter(TextureCall.class::isInstance)
                .map(TextureCall.class::cast)
                .toList();
    }

    public Optional<Tooltip> tooltip() {
        return calls.stream()
                .filter(Tooltip.class::isInstance)
                .map(Tooltip.class::cast)
                .findFirst();
    }

    /** Whether any text drawn contains {@code fragment}. */
    public boolean drewText(String fragment) {
        return texts().stream().anyMatch(t -> t.text().contains(fragment));
    }

    public int layer() {
        return layer;
    }

    public int clipDepth() {
        return clipDepth;
    }
}
