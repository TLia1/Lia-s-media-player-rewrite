package com.lia.mediaplayer.platform.mc;

import com.lia.mediaplayer.api.text.Text;
import net.minecraft.network.chat.Component;

/** Converts the mod's {@link Text} into the game's {@link Component}. */
public final class McText {

    private McText() {}

    public static Component toComponent(Text text) {
        return switch (text) {
            case Text.Literal literal -> Component.literal(literal.text());
            case Text.Translatable translatable -> Component.translatable(
                    translatable.key(),
                    translatable.args().stream()
                            .map(arg -> arg instanceof Text nested ? toComponent(nested) : arg)
                            .toArray());
        };
    }
}
