package com.lia.mediaplayer.api.source;

import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.text.Text;

/**
 * A link the mod recognised, before anything was fetched.
 *
 * @param url the link as posted; always passes {@link WebUrls#isWebUrl}
 * @param kind what it points at
 * @param sourceId the {@link LinkSource#id()} that claimed it, e.g. {@code "liasmediaplayer:youtube"}
 * @param label what chat shows in its place, e.g. {@code [youtube]}
 */
public record MediaRef(String url, MediaKind kind, String sourceId, Text label) {

    public MediaRef {
        WebUrls.require(url);
        if (sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId must not be blank");
        }
    }
}
