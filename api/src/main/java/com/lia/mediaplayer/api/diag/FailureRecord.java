package com.lia.mediaplayer.api.diag;

import com.lia.mediaplayer.api.text.Text;

/**
 * Why a track could not be played, as the error panel shows it.
 *
 * @param url the link that failed
 * @param causeId a stable id for the kind of failure: {@code "unavailable"}, {@code "private"},
 *     {@code "geo_blocked"}, {@code "tool_missing"}, … — new ones may appear, so a {@code switch}
 *     over it needs a default
 * @param message one translated line for the player
 * @param detail what the tool said, for logs; not translated
 * @since 4.0.0
 */
public record FailureRecord(String url, String causeId, Text message, String detail) {}
