package com.lia.mediaplayer.core.library;

import java.util.Optional;

/**
 * What a list shows of a link, kept on disk so a second visit asks nobody.
 *
 * @param title empty when the link has none (a bare file)
 * @param thumbnail where its picture is, when its site gives one
 */
public record LinkInfo(String title, Optional<String> thumbnail) {

    public static final LinkInfo NONE = new LinkInfo("", Optional.empty());
}
