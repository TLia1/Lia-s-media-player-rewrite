package com.lia.mediaplayer.platform.mc;

import com.lia.mediaplayer.core.link.ChatLink;
import com.lia.mediaplayer.core.link.LinkScanner;
import com.lia.mediaplayer.ui.ClientEvents;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The chat, as the mod sees it: links rewritten into labels when a message is shown, and the
 * link under the cursor.
 *
 * <p><b>Rewriting.</b> The chat mixin hands every message to {@link #displayed} just before the
 * chat stores it, on both loaders, whatever its origin. A message without {@code http} is
 * returned as it is. Otherwise it is flattened into styled runs, the UI says which spans become
 * labels, and the message is rebuilt: each label keeps the style of the text it replaces, gains
 * the colour the UI chose, a hover hint and an {@code OPEN_URL} click to the original link —
 * so if the mod's own click handling is ever bypassed, vanilla's "open link?" prompt still
 * works. Only what is shown changes; message signatures are untouched.
 *
 * <p><b>The static.</b> A mixin can only reach the mod through a static, so this class holds
 * the one the running mod installs. It is the only static state of the mod beyond the
 * entrypoints' own reference, and the sender of the message being shown, which the second
 * mixin sets around vanilla's display of a player's message (the chat itself is not told who
 * sent it).
 */
public final class McChat {

    private static final Logger LOGGER = LoggerFactory.getLogger(McChat.class);
    private static volatile @Nullable ClientEvents installed;
    private static @Nullable String showingSender;

    private double cachedX = Double.NaN;
    private double cachedY = Double.NaN;
    private @Nullable String cachedLink;
    private boolean cacheValid;

    McChat() {}

    /** Called once by the running mod; the mixins do nothing before. */
    static void install(@Nullable ClientEvents events) {
        installed = events;
    }

    // ---- Called by the mixins -------------------------------------------------

    /** A player's message is about to be shown, by {@code sender}. */
    public static void senderShowing(@Nullable String sender) {
        showingSender = sender;
    }

    /** The message to show in place of {@code message}. Never throws. */
    public static Component displayed(Component message) {
        String sender = showingSender;
        showingSender = null;
        ClientEvents events = installed;
        if (events == null) {
            return message;
        }
        try {
            return rewrite(message, sender, events);
        } catch (RuntimeException e) {
            // A broken rewrite must never take a message away.
            LOGGER.error("Could not rewrite a chat message", e);
            return message;
        }
    }

    static Component rewrite(Component message, @Nullable String sender, ClientEvents events) {
        var runs = new ArrayList<String>();
        var styles = new ArrayList<Style>();
        message.visit(
                (style, text) -> {
                    runs.add(text);
                    styles.add(style);
                    return Optional.empty();
                },
                Style.EMPTY);
        if (runs.stream().noneMatch(LinkScanner::mayContainLink)) {
            return message;
        }
        List<ChatLink> links = events.chatDisplayed(runs, sender);
        if (links.isEmpty()) {
            return message;
        }
        MutableComponent rebuilt = Component.empty();
        int next = 0;
        for (int run = 0; run < runs.size(); run++) {
            String text = runs.get(run);
            Style style = styles.get(run);
            int position = 0;
            while (next < links.size() && links.get(next).run() == run) {
                ChatLink link = links.get(next++);
                if (link.start() > position) {
                    rebuilt.append(Component.literal(text.substring(position, link.start())).setStyle(style));
                }
                rebuilt.append(McText.toComponent(link.label()).copy().setStyle(labelStyle(style, link)));
                position = link.end();
            }
            if (position < text.length()) {
                rebuilt.append(Component.literal(text.substring(position)).setStyle(style));
            }
        }
        return rebuilt;
    }

    private static Style labelStyle(Style inherited, ChatLink link) {
        Style style = inherited
                .withColor(TextColor.fromRgb(link.rgb()))
                .withUnderlined(link.underlined())
                .withClickEvent(McText.openUrl(link.url()));
        if (link.hint().isPresent()) {
            style = style.withHoverEvent(McText.showText(McText.toComponent(link.hint().get())));
        }
        return style;
    }

    // ---- The link under the cursor ----------------------------------------------

    /**
     * The address the chat text under {@code (x, y)} opens. Asked every frame the chat is open;
     * the answer is kept while the cursor stays put, until the next tick (a new message or a
     * scroll moves the lines under a still cursor).
     */
    Optional<String> linkAt(double x, double y) {
        if (!cacheValid || x != cachedX || y != cachedY) {
            cachedLink = McText.clickedUrl(Minecraft.getInstance().gui.getChat().getClickedComponentStyleAt(x, y));
            cachedX = x;
            cachedY = y;
            cacheValid = true;
        }
        return Optional.ofNullable(cachedLink);
    }

    /** Forgets the cached answer. Once a client tick. */
    void tick() {
        cacheValid = false;
        cachedLink = null;
    }
}
