package com.lia.mediaplayer.platform.mc;

import com.lia.mediaplayer.core.platform.TextMeasurer;
import com.lia.mediaplayer.ui.input.InputEvent;
import com.lia.mediaplayer.ui.screen.UiScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

/**
 * The one vanilla screen of the mod. It hosts a {@link UiScreen}: draws it through a canvas,
 * forwards its input, and answers vanilla's questions from it. Every screen of the mod is a
 * {@code UiScreen}, so this is the only place the mod depends on vanilla's screen API.
 *
 * <p>The loader's screen events still fire around it, so the media windows draw over it and
 * take their input first, as they do over the chat.
 */
public final class HostScreen extends Screen {

    private final UiScreen hosted;
    private final McTextures textures;
    private final TextMeasurer measurer;

    HostScreen(UiScreen hosted, McTextures textures, TextMeasurer measurer) {
        super(McText.toComponent(hosted.title()));
        this.hosted = hosted;
        this.textures = textures;
        this.measurer = measurer;
    }

    public UiScreen hosted() {
        return hosted;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (hosted.drawsBackground()) {
            renderBackground(graphics, mouseX, mouseY, partialTick);
        }
        var canvas = new McCanvas(graphics, textures, measurer);
        hosted.render(canvas, mouseX, mouseY, McInput.currentMods());
        canvas.finish();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return hosted.input(McInput.press(mouseX, mouseY, button));
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return hosted.input(McInput.release(mouseX, mouseY, button));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return hosted.input(McInput.scroll(mouseX, mouseY, scrollY));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        return hosted.input(McInput.key(keyCode, modifiers)) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return hosted.input(new InputEvent.CharTyped(codePoint));
    }

    @Override
    public boolean isPauseScreen() {
        return hosted.isPauseScreen();
    }

    @Override
    public void removed() {
        hosted.removed();
    }
}
