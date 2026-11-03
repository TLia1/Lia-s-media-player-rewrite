package com.lia.mediaplayer.platform.mc;

import com.lia.mediaplayer.core.platform.TextMeasurer;
import com.lia.mediaplayer.ui.input.InputEvent;
import com.lia.mediaplayer.ui.screen.UiScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;
//? if >=1.21.11 {
/*import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
*///?}

/**
 * The one vanilla screen of the mod. It hosts a {@link UiScreen}: draws it through a canvas,
 * forwards its input, and answers vanilla's questions from it. Every screen of the mod is a
 * {@code UiScreen}, so this is the only place the mod depends on vanilla's screen API.
 *
 * <p>The loader's screen events still fire around it, so the media windows draw over it and
 * take their input first, as they do over the chat. Closing it goes back to the screen it was
 * opened over, if any: the pause menu for the settings, the playlists for the history.
 *
 * <p>Two of vanilla's changes show here: 1.21.11 passes input as records ({@code
 * MouseButtonEvent}, {@code KeyEvent}, {@code CharacterEvent}) rather than loose arguments, and
 * 26.1 extracts a screen's render state rather than rendering it.
 */
public final class HostScreen extends Screen {

    private final UiScreen hosted;
    private final McTextures textures;
    private final TextMeasurer measurer;
    private final @Nullable Screen parent;

    HostScreen(UiScreen hosted, McTextures textures, TextMeasurer measurer, @Nullable Screen parent) {
        super(McText.toComponent(hosted.title()));
        this.hosted = hosted;
        this.textures = textures;
        this.measurer = measurer;
        this.parent = parent;
    }

    public UiScreen hosted() {
        return hosted;
    }

    //? if <26.1 {
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Newer versions draw the background in Screen's outer rendering pass.
        //? if <1.21.11 {
        if (hosted.drawsBackground()) {
            renderBackground(graphics, mouseX, mouseY, partialTick);
        }
        //?}
        draw(graphics, mouseX, mouseY);
    }
    //?} else {
    /*@Override
    public void extractRenderState(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        draw(graphics, mouseX, mouseY);
    }
    *///?}

    // Let the outer pass draw the background once, while keeping media controls transparent.
    // Calling it again from render/extractRenderState crashes outside a world (duplicate blur).
    //? if >=1.21.11 && <26.1 {
    /*@Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (hosted.drawsBackground()) {
            super.renderBackground(graphics, mouseX, mouseY, partialTick);
        }
    }
    *///?} elif >=26.1 {
    /*@Override
    public void extractBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (hosted.drawsBackground()) {
            super.extractBackground(graphics, mouseX, mouseY, partialTick);
        }
    }
    *///?}

    private void draw(GuiGraphics graphics, int mouseX, int mouseY) {
        var canvas = new McCanvas(graphics, textures, measurer);
        hosted.render(canvas, mouseX, mouseY, McInput.currentMods());
        canvas.finish();
    }

    //? if <1.21.11 {
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return hosted.input(McInput.press(mouseX, mouseY, button));
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return hosted.input(McInput.release(mouseX, mouseY, button));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_ESCAPE) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        return hosted.input(McInput.key(keyCode, modifiers)) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return hosted.input(new InputEvent.CharTyped(codePoint));
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return hosted.input(McInput.press(event.x(), event.y(), event.button()));
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return hosted.input(McInput.release(event.x(), event.y(), event.button()));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            return super.keyPressed(event);
        }
        return hosted.input(McInput.key(event.key(), event.modifiers())) || super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return hosted.input(new InputEvent.CharTyped(event.codepoint()));
    }
    *///?}

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return hosted.input(McInput.scroll(mouseX, mouseY, scrollY));
    }

    @Override
    public boolean isPauseScreen() {
        return hosted.isPauseScreen();
    }

    @Override
    public void onClose() {
        McScreens.show(parent);
    }

    @Override
    public void removed() {
        hosted.removed();
    }
}
