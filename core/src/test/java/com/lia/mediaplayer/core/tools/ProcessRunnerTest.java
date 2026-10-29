package com.lia.mediaplayer.core.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.lia.mediaplayer.core.testing.FakeYtDlp;
import com.lia.mediaplayer.core.testing.TestClientThread;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProcessRunnerTest {

    private final TestClientThread client = new TestClientThread();
    private final ProcessRunner runner = new ProcessRunner(client);

    @BeforeEach
    void posixShell() {
        assumeTrue(FakeYtDlp.isSupported(), "uses /bin/sh");
    }

    @AfterEach
    void close() {
        runner.close();
        client.close();
    }

    private static List<String> sh(String script) {
        return List.of("/bin/sh", "-c", script);
    }

    @Test
    void returnsWhatTheProgramPrinted() throws IOException {
        ProcessResult result = runner.run(sh("echo out; echo err >&2; exit 3"), Duration.ofSeconds(10), 1024);
        assertThat(result.exitCode()).isEqualTo(3);
        assertThat(result.succeeded()).isFalse();
        assertThat(result.stdout()).isEqualTo("out\n");
        assertThat(result.stderr()).isEqualTo("err");
    }

    @Test
    void killsWhatRunsTooLongOrPrintsTooMuch() {
        assertThatThrownBy(() -> runner.run(sh("sleep 30"), Duration.ofMillis(200), 1024))
                .hasMessageContaining("timed out");
        assertThatThrownBy(() -> runner.run(sh("yes"), Duration.ofSeconds(10), 4096))
                .hasMessageContaining("exceeded 4096 bytes");
        assertThat(runner.runningCount()).isZero();
    }

    @Test
    void namesAProgramThatCannotStart() {
        assertThatThrownBy(() -> runner.run(List.of("/nonexistent/tool"), Duration.ofSeconds(1), 1))
                .hasMessageContaining("Cannot run program '/nonexistent/tool'");
    }

    @Test
    void closingKillsTheProgramAndItsChildren() throws Exception {
        ManagedProcess process = runner.start("tree", sh("sleep 30 & sleep 30; wait"));
        assertThat(process.name()).isEqualTo("tree");
        assertThat(process.toString()).startsWith("tree (pid ");
        Thread.sleep(100);
        process.close();
        assertThat(process.waitFor(Duration.ofSeconds(5))).isTrue();
        assertThat(process.wasKilled()).isTrue();
        assertThat(process.isAlive()).isFalse();
    }

    @Test
    void closingTheRunnerKillsEverythingAndRefusesMore() throws Exception {
        ManagedProcess process = runner.start("sleeper", sh("sleep 30"));
        runner.close();
        assertThat(process.onExit().get().isAlive()).isFalse();
        assertThatThrownBy(() -> runner.start("late", sh("true"))).hasMessageContaining("closed");
    }

    @Test
    void neverBlocksTheClientThread() {
        assertThatThrownBy(() -> client.call(() -> runner.run(sh("true"), Duration.ofSeconds(1), 1)))
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("client thread");
    }

    @Test
    void theTailKeepsTheLastLines() {
        var tail = new OutputTail(10);
        tail.append("first line");
        tail.append("second");
        tail.append("third");
        assertThat(tail.text()).isEqualTo("third");
        tail.append("a very long line indeed");
        assertThat(tail.text()).isEqualTo("ine indeed");
        assertThatThrownBy(() -> new OutputTail(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
