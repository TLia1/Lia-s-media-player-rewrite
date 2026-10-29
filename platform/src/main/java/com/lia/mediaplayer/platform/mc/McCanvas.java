package com.lia.mediaplayer.platform.mc;

import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.platform.TextMeasurer;
import com.lia.mediaplayer.core.platform.TextureRef;
import com.lia.mediaplayer.ui.canvas.Canvas;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The UI's {@link Canvas} over one frame of the game's GUI drawing.
 *
 * <p>On 1.21.1 every draw call goes to the screen at once (the graphics object is not in its
 * batched mode), so drawing order is stacking order. {@link #nextLayer()} still flushes and
 * steps the depth forward a little, so a layer stays above the last even where the game
 * batches. Tooltips are kept until {@link #finish()}, which draws them last, above
 * everything.
 */
final class McCanvas implements Canvas {

    /** Depth step per layer: small, so many windows stay far below vanilla's tooltips (400). */
    private static final float LAYER_DEPTH = 1;

    private final GuiGraphics graphics;
    private final McTextures textures;
    private final TextMeasurer measurer;
    private List<Text> tooltipLines = List.of();
    private int tooltipX;
    private int tooltipY;
    private int clipDepth;

    McCanvas(GuiGraphics graphics, McTextures textures, TextMeasurer measurer) {
        this.graphics = graphics;
        this.textures = textures;
        this.measurer = measurer;
        graphics.pose().pushPose();
    }

    @Override
    public int width() {
        return graphics.guiWidth();
    }

    @Override
    public int height() {
        return graphics.guiHeight();
    }

    @Override
    public void fill(int x0, int y0, int x1, int y1, int argb) {
        if (x1 > x0 && y1 > y0 && (argb >>> 24) != 0) {
            graphics.fill(x0, y0, x1, y1, argb);
        }
    }

    @Override
    public void texture(TextureRef texture, int x, int y, int w, int h) {
        ResourceLocation location = textures.locationOf(texture);
        if (location == null || w <= 0 || h <= 0) {
            return;
        }
        int tw = texture.width();
        int th = texture.height();
        graphics.blit(location, x, y, w, h, 0f, 0f, tw, th, tw, th);
    }

    @Override
    public void text(Text text, int x, int y, int argb, boolean shadow) {
        graphics.drawString(Minecraft.getInstance().font, McText.toComponent(text), x, y, argb, shadow);
    }

    @Override
    public TextMeasurer measurer() {
        return measurer;
    }

    @Override
    public void pushClip(int x0, int y0, int x1, int y1) {
        graphics.enableScissor(x0, y0, x1, y1);
        clipDepth++;
    }

    @Override
    public void popClip() {
        if (clipDepth > 0) {
            clipDepth--;
            graphics.disableScissor();
        }
    }

    @Override
    public void nextLayer() {
        graphics.flush();
        graphics.pose().translate(0, 0, LAYER_DEPTH);
    }

    @Override
    public void tooltip(List<Text> lines, int x, int y) {
        tooltipLines = List.copyOf(lines);
        tooltipX = x;
        tooltipY = y;
    }

    /** Ends the frame: unwinds clips and depth, and draws the tooltip, if any, above all. */
    void finish() {
        while (clipDepth > 0) {
            popClip();
        }
        graphics.flush();
        graphics.pose().popPose();
        if (!tooltipLines.isEmpty()) {
            List<Component> components = tooltipLines.stream().map(McText::toComponent).toList();
            graphics.renderComponentTooltip(Minecraft.getInstance().font, components, tooltipX, tooltipY);
        }
    }
}
