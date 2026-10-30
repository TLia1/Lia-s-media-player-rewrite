package com.lia.mediaplayer.core.resolve;

import com.lia.mediaplayer.api.source.MediaMetadata;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.api.source.ResolveOptions;
import com.lia.mediaplayer.api.source.ResolvedMedia;
import com.lia.mediaplayer.api.source.Resolver;
import com.lia.mediaplayer.api.source.StreamUrl;
import com.lia.mediaplayer.api.source.WebUrls;
import com.lia.mediaplayer.core.ModInfo;
import com.lia.mediaplayer.core.link.BuiltinSources;
import com.lia.mediaplayer.core.net.Http;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.InstantSource;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Tenor share page to the GIF behind it — the link Discord sends when someone picks a GIF.
 *
 * <p>The page is server-rendered with its media in the markup ({@code
 * media1.tenor.com/m/<id>/name.gif}). Those addresses are hot-link protected, so the media id is
 * taken from them and the GIF is fetched from Tenor's direct endpoint, {@code
 * c.tenor.com/<id>/tenor.gif}. Older pages carry only an {@code og:image}, used as it is.
 */
public final class TenorResolver implements Resolver {

    static final long MAX_PAGE_BYTES = 512 * 1024;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Duration VALIDITY = Duration.ofDays(1);

    private static final String MEDIA = "https?://(?:media\\d*|c)\\.tenor\\.com/m/([A-Za-z0-9_-]{8,})/";
    /** The picture the page displays: its high-resolution variant. */
    private static final Pattern IMG_MEDIA =
            Pattern.compile("<img[^>]+src\\s*=\\s*[\"']" + MEDIA + "[^\"']+\\.gif[\"']", Pattern.CASE_INSENSITIVE);
    /** {@code <meta itemprop="contentUrl" content="…">}, the attributes in either order. */
    private static final Pattern CONTENT_URL_PROP_FIRST = Pattern.compile(
            "itemprop\\s*=\\s*[\"']contentUrl[\"'][^>]*content\\s*=\\s*[\"']" + MEDIA, Pattern.CASE_INSENSITIVE);

    private static final Pattern CONTENT_URL_CONTENT_FIRST = Pattern.compile(
            "content\\s*=\\s*[\"']" + MEDIA + "[^\"']+[\"'][^>]*itemprop\\s*=\\s*[\"']contentUrl[\"']",
            Pattern.CASE_INSENSITIVE);
    /** Any media of the page, as a last resort. */
    private static final Pattern ANY_MEDIA = Pattern.compile(MEDIA, Pattern.CASE_INSENSITIVE);

    private static final Pattern OG_IMAGE = Pattern.compile(
            "property\\s*=\\s*[\"']og:image[\"'][^>]*content\\s*=\\s*[\"']([^\"']+\\.gif)[\"']"
                    + "|content\\s*=\\s*[\"']([^\"']+\\.gif)[\"'][^>]*property\\s*=\\s*[\"']og:image[\"']",
            Pattern.CASE_INSENSITIVE);

    private final Http http;
    private final Executor io;
    private final InstantSource clock;

    public TenorResolver(Http http, Executor io, InstantSource clock) {
        this.http = http;
        this.io = io;
        this.clock = clock;
    }

    @Override
    public String id() {
        return ModInfo.MOD_ID + ":tenor";
    }

    @Override
    public boolean handles(MediaRef ref) {
        return ref.sourceId().equals(BuiltinSources.TENOR);
    }

    @Override
    public CompletableFuture<ResolvedMedia> resolve(MediaRef ref, ResolveOptions options) {
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        byte[] page = http.get(URI.create(ref.url()), MAX_PAGE_BYTES, TIMEOUT);
                        String gif = gifOf(new String(page, StandardCharsets.UTF_8))
                                .orElseThrow(() -> new IOException("No GIF found on " + ref.url()));
                        return new ResolvedMedia(
                                ref,
                                Optional.of(StreamUrl.of(gif)),
                                Optional.empty(),
                                Map.of(),
                                MediaMetadata.UNKNOWN,
                                clock.instant().plus(VALIDITY));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                },
                io);
    }

    /** The GIF a Tenor page shows, from its markup. */
    static Optional<String> gifOf(String html) {
        for (Pattern pattern :
                new Pattern[] {IMG_MEDIA, CONTENT_URL_PROP_FIRST, CONTENT_URL_CONTENT_FIRST, ANY_MEDIA}) {
            Matcher matcher = pattern.matcher(html);
            if (matcher.find()) {
                return Optional.of("https://c.tenor.com/" + matcher.group(1) + "/tenor.gif");
            }
        }
        Matcher og = OG_IMAGE.matcher(html);
        if (og.find()) {
            String first = og.group(1);
            String url = first != null ? first : og.group(2);
            if (url == null) {
                return Optional.empty();
            }
            String unescaped = url.replace("&amp;", "&").replace("&#x2F;", "/").replace("&#47;", "/");
            // The page is anyone's to write; only a web address goes further.
            return Optional.of(unescaped).filter(WebUrls::isWebUrl);
        }
        return Optional.empty();
    }
}
