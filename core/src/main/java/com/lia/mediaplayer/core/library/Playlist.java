package com.lia.mediaplayer.core.library;

import com.lia.mediaplayer.api.source.WebUrls;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A named, ordered list of links. Immutable: every change gives a new playlist. Only {@code
 * http(s)} links get in — what comes out of a playlist is handed to ffmpeg and yt-dlp, which
 * must never be given a local path — and the name is never blank.
 *
 * @param id stable while the playlist exists, so a screen can follow it through a rename
 */
public record Playlist(String id, String name, List<String> urls) {

    /** Longest name kept. */
    public static final int MAX_NAME = 64;

    public Playlist {
        name = cleanName(name);
        urls = urls.stream().filter(WebUrls::isWebUrl).toList();
    }

    public int size() {
        return urls.size();
    }

    public Playlist withName(String newName) {
        return new Playlist(id, newName, urls);
    }

    /** With {@code more} appended, in order; duplicates are kept, a track may be listed twice. */
    public Playlist withAdded(Collection<String> more) {
        var list = new ArrayList<>(urls);
        list.addAll(more);
        return new Playlist(id, name, list);
    }

    public Playlist withRemoved(int index) {
        if (index < 0 || index >= urls.size()) {
            return this;
        }
        var list = new ArrayList<>(urls);
        list.remove(index);
        return new Playlist(id, name, list);
    }

    /**
     * Moves the entry at {@code from} to the gap before what is now at {@code insertBefore}
     * ({@code size()} is the end): the shape of a drop between two rows, where which gap was
     * meant does not depend on the row being moved.
     */
    public Playlist withMoved(int from, int insertBefore) {
        if (from < 0 || from >= urls.size()) {
            return this;
        }
        int target = Math.max(0, Math.min(urls.size(), insertBefore));
        if (target == from || target == from + 1) {
            return this;
        }
        var list = new ArrayList<>(urls);
        String url = list.remove(from);
        list.add(target > from ? target - 1 : target, url);
        return new Playlist(id, name, list);
    }

    static String cleanName(String raw) {
        String oneLine = raw.replace('\n', ' ').replace('\r', ' ').strip();
        if (oneLine.isEmpty()) {
            return "Playlist";
        }
        return oneLine.length() > MAX_NAME ? oneLine.substring(0, MAX_NAME).strip() : oneLine;
    }
}
