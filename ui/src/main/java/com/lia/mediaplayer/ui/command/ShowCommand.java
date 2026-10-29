package com.lia.mediaplayer.ui.command;

import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.link.SourceRegistry;
import com.lia.mediaplayer.core.text.Keys;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code /show <type> <url> [newPlayer]}: plays a link without it being posted in chat.
 *
 * <p>The type says how to show it and must agree with what the link is: a video can be shown
 * as audio only, nothing else changes kind. Without {@code newPlayer} the link joins the queue
 * of the front window of its kind, as a chat click does; with it, it opens a window of its
 * own.
 */
public final class ShowCommand {

    /** How the link should be shown. */
    public enum Type {
        VIDEO,
        AUDIO,
        IMAGE;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** What actually plays a link once the command has accepted it. */
    @FunctionalInterface
    public interface Target {
        void play(MediaRef ref, boolean audioOnly, boolean newWindow);
    }

    private final SourceRegistry sources;
    private final Target target;

    public ShowCommand(SourceRegistry sources, Target target) {
        this.sources = sources;
        this.target = target;
    }

    /** The values the {@code type} argument takes, for suggestions. */
    public static List<String> types() {
        return List.of(Type.VIDEO.id(), Type.AUDIO.id(), Type.IMAGE.id());
    }

    /**
     * Runs the command from the rest of its line: the link, optionally in quotes, then an
     * optional {@code true} or {@code false} for {@code newPlayer}. Taking the rest of the line
     * means a link needs no quotes, whatever characters it holds.
     *
     * @return why it refused, or empty if the link is playing
     */
    public Optional<Text> run(String type, String rest) {
        String url = rest.strip();
        boolean newWindow = false;
        int space = url.lastIndexOf(' ');
        if (space > 0) {
            String last = url.substring(space + 1);
            if (last.equalsIgnoreCase("true") || last.equalsIgnoreCase("false")) {
                newWindow = Boolean.parseBoolean(last);
                url = url.substring(0, space).strip();
            }
        }
        if (url.length() >= 2 && url.startsWith("\"") && url.endsWith("\"")) {
            url = url.substring(1, url.length() - 1);
        }
        return run(type, url, newWindow);
    }

    /**
     * Runs the command.
     *
     * @return why it refused, or empty if the link is playing
     */
    public Optional<Text> run(String type, String url, boolean newWindow) {
        Type asked;
        try {
            asked = Type.valueOf(type.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Optional.of(Text.translatable(Keys.COMMAND_INVALID_TYPE, type));
        }
        Optional<MediaRef> match = sources.match(url.strip());
        if (match.isEmpty()) {
            return Optional.of(Text.translatable(Keys.COMMAND_UNSUPPORTED_URL));
        }
        MediaRef ref = match.get();
        boolean video = ref.kind() == MediaKind.VIDEO || ref.kind() == MediaKind.COLLECTION;
        switch (asked) {
            case VIDEO -> {
                if (!video) {
                    return mismatch(ref, asked);
                }
                target.play(ref, false, newWindow);
            }
            case AUDIO -> {
                if (!video && ref.kind() != MediaKind.AUDIO) {
                    return mismatch(ref, asked);
                }
                target.play(ref, true, newWindow);
            }
            case IMAGE -> {
                if (ref.kind() != MediaKind.IMAGE) {
                    return mismatch(ref, asked);
                }
                return Optional.of(Text.translatable(Keys.COMMAND_IMAGES_LATER));
            }
        }
        return Optional.empty();
    }

    private static Optional<Text> mismatch(MediaRef ref, Type asked) {
        return Optional.of(
                Text.translatable(Keys.COMMAND_KIND_MISMATCH, ref.kind().name().toLowerCase(Locale.ROOT), asked.id()));
    }
}
