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
//? if >=1.21.2 && <1.21.6
/*import net.minecraft.client.renderer.RenderType;*/
//? if >=1.21.6 {
/*import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.renderer.RenderPipelines;
*///?}

/**
 * The UI's {@link Canvas} over one frame of the game's GUI drawing.
 *
 * <p>Before 1.21.6 every draw call goes to the screen at once (the graphics object is not in its
 * batched mode), so drawing order is stacking order. {@link #nextLayer()} still flushes and
 * steps the depth forward a little, so a layer stays above the last even where the game
 * batches. From 1.21.6 draws are recorded and replayed at the end of the frame, sorted by
 * stratum: a layer is a new stratum, and there is nothing to flush or unwind. From 26.1 the
 * graphics object is an extractor, and the textures it names are drawn after the frame is
 * extracted — which is why textures are released between ticks, never during a frame.
 *
 * <p>Tooltips are kept until {@link #finish()}, which draws them last, above everything, with
 * the immediate form of the tooltip call (the deferred one would wait for the screen's own
 * tooltip pass, which a loader's after-render event comes after).
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
        //? if <1.21.6
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
    public double pixelScale() {
        int guiHeight = graphics.guiHeight();
        return guiHeight <= 0 ? 1 : Minecraft.getInstance().getWindow().getHeight() / (double) guiHeight;
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
        //? if <1.21.2 {
        graphics.blit(location, x, y, w, h, 0f, 0f, tw, th, tw, th);
        //?} elif <1.21.6 {
        /*graphics.blit(RenderType::guiTextured, location, x, y, 0f, 0f, w, h, tw, th, tw, th);
        *///?} else
        /*graphics.blit(RenderPipelines.GUI_TEXTURED, location, x, y, 0f, 0f, w, h, tw, th, tw, th);*/
    }

    @Override
    public void text(Text text, int x, int y, int argb, boolean shadow) {
        var font = Minecraft.getInstance().font;
        // Before 1.21.6 the font made a colour with no alpha opaque; from 1.21.6 it is drawn
        // transparent. Doing it here keeps one meaning on every version.
        int color = (argb & 0xFC000000) == 0 ? argb | 0xFF000000 : argb;
        //? if <26.1 {
        graphics.drawString(font, McText.toComponent(text), x, y, color, shadow);
        //?} else
        /*graphics.text(font, McText.toComponent(text), x, y, color, shadow);*/
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
        //? if <1.21.6 {
        graphics.flush();
        graphics.pose().translate(0, 0, LAYER_DEPTH);
        //?} else
        /*graphics.nextStratum();*/
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
        //? if <1.21.6 {
        graphics.flush();
        graphics.pose().popPose();
        //?}
        if (!tooltipLines.isEmpty()) {
            drawTooltip(tooltipLines.stream().map(McText::toComponent).toList());
        }
    }

    private void drawTooltip(List<Component> lines) {
        var font = Minecraft.getInstance().font;
        //? if <1.21.6 {
        graphics.renderComponentTooltip(font, lines, tooltipX, tooltipY);
        //?} else {
        /*graphics.nextStratum();
        var components = lines.stream()
                .map(line -> ClientTooltipComponent.create(line.getVisualOrderText()))
                .toList();
        // 1.21.6 renamed the immediate overloads away (setTooltipForNextFrame defers); this one
        // stayed, renamed `tooltip` in 26.1, and 26.3 added a flag every plain tooltip passes as
        // false.
        //? if <26.1 {
        graphics.renderTooltip(font, components, tooltipX, tooltipY, DefaultTooltipPositioner.INSTANCE, null);
        //?} elif <26.3 {
        /^graphics.tooltip(font, components, tooltipX, tooltipY, DefaultTooltipPositioner.INSTANCE, null);
        ^///?} else
        /^graphics.tooltip(font, components, tooltipX, tooltipY, DefaultTooltipPositioner.INSTANCE, null, false);^/
        *///?}
    }
}
