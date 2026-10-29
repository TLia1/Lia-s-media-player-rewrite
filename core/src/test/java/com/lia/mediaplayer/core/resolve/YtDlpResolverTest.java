package com.lia.mediaplayer.core.resolve;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.api.source.ResolveOptions;
import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.link.BuiltinSources;
import com.lia.mediaplayer.core.link.SourceRegistry;
import com.lia.mediaplayer.core.testing.FakeYtDlp;
import com.lia.mediaplayer.core.testing.TestClientThread;
import com.lia.mediaplayer.core.tools.LocatedTool;
import com.lia.mediaplayer.core.tools.ProcessRunner;
import com.lia.mediaplayer.core.tools.Tool;
import com.lia.mediaplayer.core.tools.ToolMissingException;
import com.lia.mediaplayer.core.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.InstantSource;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YtDlpResolverTest {

    @TempDir
    Path dir;

    private final TestClientThread client = new TestClientThread();
    private final ProcessRunner runner = new ProcessRunner(client);
    private final java.util.concurrent.ExecutorService io = Executors.newVirtualThreadPerTaskExecutor();
    private final SourceRegistry sources = new SourceRegistry(BuiltinSources.all());

    @BeforeEach
    void posixOnly() {
        assumeTrue(FakeYtDlp.isSupported(), "the fake yt-dlp is a shell script");
    }

    @AfterEach
    void close() {
        runner.close();
        io.shutdownNow();
        client.close();
    }

    private YtDlpResolver resolver(FakeYtDlp ytDlp, String clients) {
        var tools = ToolProvider.fixed(Map.of(Tool.YT_DLP, new LocatedTool(Tool.YT_DLP, ytDlp.path(), "test")));
        return new YtDlpResolver(tools, runner, io, InstantSource.system(), Duration.ofSeconds(10), clients);
    }

    @Test
    void runsYtDlpSafelyAndReadsItsAnswer() throws Exception {
        var ytDlp = FakeYtDlp.create(dir, "{\"url\": \"https://cdn.example/v.mp4\", \"title\": \"T\"}", "", 0);
        MediaRef ref = sources.match("https://youtu.be/abcdefghijk").orElseThrow();

        var media = resolver(ytDlp, "android,default")
                .resolve(ref, ResolveOptions.DEFAULT)
                .get();

        assertThat(media.metadata().title()).contains("T");
        assertThat(ytDlp.lastArguments())
                .containsSubsequence("--ignore-config", "--no-playlist", "-J")
                .containsSubsequence("--extractor-args", "youtube:player_client=android,default")
                .containsSubsequence("-f", "bv*[height<=720]+ba/b[height<=720]/b", "--", ref.url())
                .endsWith(ref.url());
    }

    @Test
    void asksForSoundOnlyWhenThePictureWillNotBeShownAndLeavesOtherSitesAlone() throws Exception {
        var ytDlp = FakeYtDlp.create(dir, "{\"url\": \"https://cdn.example/a.mp3\", \"vcodec\": \"none\"}", "", 0);
        MediaRef ref = sources.match("https://soundcloud.com/artist/track").orElseThrow();

        resolver(ytDlp, "android").resolve(ref, ResolveOptions.DEFAULT).get();

        assertThat(ytDlp.lastArguments()).containsSubsequence("-f", "ba/b").doesNotContain("--extractor-args");
    }

    @Test
    void reportsYtDlpsOwnWordsOnFailure() throws IOException {
        var ytDlp = FakeYtDlp.create(dir, "", "ERROR: [youtube] abc: Private video", 1);
        MediaRef ref = sources.match("https://www.youtube.com/watch?v=abc").orElseThrow();

        assertThatThrownBy(() ->
                        resolver(ytDlp, "").resolve(ref, ResolveOptions.DEFAULT).get())
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining("yt-dlp: ERROR: [youtube] abc: Private video");
    }

    @Test
    void failsCleanlyWithoutYtDlp() {
        var resolver = new YtDlpResolver(
                ToolProvider.fixed(Map.of()), runner, io, InstantSource.system(), Duration.ofSeconds(10), "");
        MediaRef ref = sources.match("https://www.youtube.com/watch?v=abc").orElseThrow();
        assertThatThrownBy(() -> resolver.resolve(ref, ResolveOptions.DEFAULT).get())
                .hasCauseInstanceOf(java.io.UncheckedIOException.class)
                .rootCause()
                .isInstanceOf(ToolMissingException.class)
                .hasMessageContaining("is required to play");
    }

    @Test
    void handlesEverythingPlayableExceptDirectFiles() {
        var resolver =
                new YtDlpResolver(ToolProvider.fixed(Map.of()), runner, io, InstantSource.system(), Duration.ZERO, "");
        assertThat(resolver.handles(
                        sources.match("https://www.youtube.com/watch?v=abc").orElseThrow()))
                .isTrue();
        assertThat(resolver.handles(sources.match("https://cdn.example/a.mp4").orElseThrow()))
                .isFalse();
        assertThat(resolver.handles(
                        new MediaRef("https://x.example/p", MediaKind.COLLECTION, "t:x", Text.literal("x"))))
                .isFalse();
    }

    @Test
    void expandsACollectionIntoItsEntries() throws Exception {
        String json = """
                {"_type": "playlist", "title": "Mix", "entries": [
                  {"url": "https://www.youtube.com/watch?v=aaaaaaaaaaa", "title": "A"},
                  {"ie_key": "Youtube", "id": "bbbbbbbbbbb"},
                  {"url": "https://cdn.example/c.mp3"},
                  {"url": "file:///etc/passwd"},
                  {"ie_key": "Youtube", "id": "-rf /"},
                  "not an object"]}
                """;
        var ytDlp = FakeYtDlp.create(dir, json, "", 0);
        var tools = ToolProvider.fixed(Map.of(Tool.YT_DLP, new LocatedTool(Tool.YT_DLP, ytDlp.path(), "test")));
        var expander = new CollectionExpander(tools, runner, io, sources, Duration.ofSeconds(10));
        MediaRef playlist =
                sources.match("https://www.youtube.com/playlist?list=PL1").orElseThrow();

        ExpandedCollection collection = expander.expand(playlist).get();

        assertThat(collection.title()).contains("Mix");
        assertThat(collection.entries())
                .extracting(MediaRef::url)
                .containsExactly(
                        "https://www.youtube.com/watch?v=aaaaaaaaaaa",
                        "https://www.youtube.com/watch?v=bbbbbbbbbbb",
                        "https://cdn.example/c.mp3");
        assertThat(collection.entries())
                .extracting(MediaRef::kind)
                .containsExactly(MediaKind.VIDEO, MediaKind.VIDEO, MediaKind.AUDIO);
        assertThat(ytDlp.lastArguments()).containsSubsequence("--flat-playlist", "--playlist-end", "500", "-J", "--");
        assertThat(expander.expand(sources.match("https://youtu.be/x").orElseThrow()))
                .isCompletedExceptionally();
    }

    @Test
    void unknownEntriesOfACollectionPlayAsItsKind() throws IOException {
        MediaRef playlist = sources.match("https://soundcloud.com/a/sets/b").orElseThrow();
        var collection = CollectionExpander.parse(
                "{\"entries\": [{\"url\": \"https://api.soundcloud.example/track/1\"}]}", playlist, sources);
        assertThat(collection.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.kind()).isEqualTo(MediaKind.AUDIO);
            assertThat(entry.sourceId()).isEqualTo(CollectionExpander.ENTRY_SOURCE);
        });
    }
}
