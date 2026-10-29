package com.lia.mediaplayer.core.link;

import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.api.text.Text;
import java.util.Optional;

/**
 * One link of a chat message to show as a label: characters {@code [start, end)} of run
 * {@code run} become {@code label}, drawn in {@code rgb}, which opens {@code ref.url()} when
 * clicked and shows {@code hint} when hovered. Everything else about the run's style — font,
 * bold, the server's own hover — is kept by the platform, which alone knows it.
 *
 * @param run the index of the styled run of the message the link is in
 * @param rgb the label's colour, {@code 0xRRGGBB}
 */
public record ChatLink(
        int run, int start, int end, MediaRef ref, Text label, Optional<Text> hint, int rgb, boolean underlined) {

    public ChatLink {
        if (run < 0 || start < 0 || end <= start) {
            throw new IllegalArgumentException("Bad span " + run + ":[" + start + ", " + end + ")");
        }
    }

    public String url() {
        return ref.url();
    }
}
