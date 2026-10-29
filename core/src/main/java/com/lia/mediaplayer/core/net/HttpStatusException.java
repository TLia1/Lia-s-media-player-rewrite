package com.lia.mediaplayer.core.net;

import java.io.IOException;
import java.net.URI;

/** A server answered with something other than 200. */
public final class HttpStatusException extends IOException {

    private static final long serialVersionUID = 1L;

    private final int status;

    public HttpStatusException(int status, URI uri) {
        super("HTTP " + status + " for " + uri);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
