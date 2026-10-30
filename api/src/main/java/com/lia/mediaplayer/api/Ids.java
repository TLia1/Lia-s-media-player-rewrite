package com.lia.mediaplayer.api;

import java.util.regex.Pattern;

/**
 * Namespaced ids, {@code "owner:name"}: how everything an addon registers is named, so two
 * addons cannot collide and the mod's files store each value under its owner.
 *
 * <p>The owner is an addon id (lower case letters, digits, {@code _ . -}); the name may also
 * hold {@code /}.
 *
 * @since 4.0.0
 */
public final class Ids {

    private static final Pattern OWNER = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern NAMESPACED = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private Ids() {}

    /** Whether {@code id} has the shape {@code owner:name}. */
    public static boolean isNamespaced(String id) {
        return NAMESPACED.matcher(id).matches();
    }

    /** Whether {@code addonId} can be the owner part of an id. */
    public static boolean isOwner(String addonId) {
        return OWNER.matcher(addonId).matches();
    }

    /**
     * Returns {@code id} if it is namespaced.
     *
     * @param what what the id names, for the message: "Setting id", "Theme id"
     * @throws IllegalArgumentException otherwise
     */
    public static String require(String id, String what) {
        if (!isNamespaced(id)) {
            throw new IllegalArgumentException(what + " must be namespaced, like owner:name — was " + id);
        }
        return id;
    }

    /** The owner part of a namespaced id: {@code "myaddon"} for {@code "myaddon:share"}. */
    public static String owner(String id) {
        int colon = id.indexOf(':');
        if (colon <= 0) {
            throw new IllegalArgumentException("Not a namespaced id: " + id);
        }
        return id.substring(0, colon);
    }
}
