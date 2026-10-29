package com.lia.mediaplayer.platform.mc;

import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.platform.TextMeasurer;
import net.minecraft.client.Minecraft;

/** Text sizes in the game's font. */
final class McTextMeasurer implements TextMeasurer {

    @Override
    public int width(Text text) {
        return Minecraft.getInstance().font.width(McText.toComponent(text));
    }

    @Override
    public int width(String text) {
        return Minecraft.getInstance().font.width(text);
    }

    @Override
    public int lineHeight() {
        // The glyphs are 8 pixels tall; the font's line height adds the gap to the next line.
        return Minecraft.getInstance().font.lineHeight - 1;
    }

    @Override
    public String resolve(Text text) {
        return McText.toComponent(text).getString();
    }
}
