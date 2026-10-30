package com.lia.mediaplayer.platform.mc;

import com.lia.mediaplayer.api.text.Text;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import org.jspecify.annotations.Nullable;
//? if >=1.21.5 {
/*import java.net.URI;
import java.net.URISyntaxException;
*///?}

/**
 * Converts the mod's {@link Text} into the game's {@link Component}, and is the mod's one
 * contact with click and hover events, which became records in 1.21.5.
 */
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

    /**
     * The click that opens {@code url}: vanilla's "open link?" prompt, if the mod ever lets it
     * through. {@code null} where the game wants a parsed address and {@code url} is not one.
     */
    static @Nullable ClickEvent openUrl(String url) {
        //? if <1.21.5 {
        return new ClickEvent(ClickEvent.Action.OPEN_URL, url);
        //?} else {
        /*try {
            return new ClickEvent.OpenUrl(new URI(url));
        } catch (URISyntaxException e) {
            return null;
        }
        *///?}
    }

    /** The hover that shows {@code text}. */
    static HoverEvent showText(Component text) {
        //? if <1.21.5 {
        return new HoverEvent(HoverEvent.Action.SHOW_TEXT, text);
        //?} else
        /*return new HoverEvent.ShowText(text);*/
    }

    /** The address a click on {@code style} opens, if it opens one. */
    static @Nullable String clickedUrl(@Nullable Style style) {
        ClickEvent click = style == null ? null : style.getClickEvent();
        //? if <1.21.5 {
        if (click == null || click.getAction() != ClickEvent.Action.OPEN_URL) {
            return null;
        }
        return click.getValue();
        //?} else
        /*return click instanceof ClickEvent.OpenUrl open ? open.uri().toString() : null;*/
    }
}
