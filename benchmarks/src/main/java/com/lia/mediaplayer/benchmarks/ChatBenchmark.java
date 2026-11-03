package com.lia.mediaplayer.benchmarks;

import com.lia.mediaplayer.core.link.BuiltinSources;
import com.lia.mediaplayer.core.link.ChatLink;
import com.lia.mediaplayer.core.link.ChatRewriter;
import com.lia.mediaplayer.core.link.SourceRegistry;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

/**
 * Every chat message passes through the rewriter as it is displayed. Budget (§12): under 5 µs
 * for a message without a link, under 100 µs for one with links.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class ChatBenchmark {

    private final ChatRewriter rewriter = new ChatRewriter(new SourceRegistry(BuiltinSources.all()));

    private final List<String> plain = List.of(
            "<Steve> ", "has anyone found the stronghold yet? I think it is past the ravine, near the big oak forest");

    private final List<String> links = List.of(
            "<Alex> ",
            "look at https://www.youtube.com/watch?v=aqz-KE-bpKQ and this one https://media.tenor.com/abc/cat.gif"
                    + " then https://soundcloud.com/artist/track for the music");

    @Benchmark
    public List<ChatLink> messageWithoutLink() {
        return rewriter.rewrite(plain, "Steve");
    }

    @Benchmark
    public List<ChatLink> messageWithThreeLinks() {
        return rewriter.rewrite(links, "Alex");
    }
}
