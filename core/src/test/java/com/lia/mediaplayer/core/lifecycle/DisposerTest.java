package com.lia.mediaplayer.core.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DisposerTest {

    private final List<String> closed = new ArrayList<>();

    private AutoCloseable resource(String name) {
        return () -> closed.add(name);
    }

    private AutoCloseable failing(String name) {
        return () -> {
            closed.add(name);
            throw new IllegalStateException(name + " failed");
        };
    }

    @Test
    void closesInReverseOrderOfRegistration() {
        var disposer = new Disposer();
        disposer.add(resource("first"));
        disposer.add(resource("second"));
        disposer.add(resource("third"));

        disposer.close();

        assertThat(closed).containsExactly("third", "second", "first");
        assertThat(disposer.isClosed()).isTrue();
    }

    @Test
    void addReturnsTheResourceItWasGiven() {
        var disposer = new Disposer();
        AutoCloseable resource = resource("x");
        assertThat(disposer.add(resource)).isSameAs(resource);
    }

    @Test
    void closesEverythingEvenWhenSomeFail() {
        var disposer = new Disposer();
        disposer.add(resource("a"));
        disposer.add(failing("b"));
        disposer.add(failing("c"));

        assertThatThrownBy(disposer::close)
                .isInstanceOf(Disposer.DisposeException.class)
                .satisfies(e -> {
                    assertThat(e.getCause()).hasMessage("c failed");
                    assertThat(e.getSuppressed())
                            .singleElement()
                            .satisfies(s -> assertThat(s).hasMessage("b failed"));
                });
        assertThat(closed).containsExactly("c", "b", "a");
    }

    @Test
    void closingTwiceIsANoOp() {
        var disposer = new Disposer();
        disposer.add(resource("only"));
        disposer.close();
        disposer.close();
        assertThat(closed).containsExactly("only");
    }

    @Test
    void refusesResourcesAfterClosing() {
        var disposer = new Disposer();
        disposer.close();
        assertThatThrownBy(() -> disposer.add(resource("late"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void keepsTheInterruptFlagWhenAResourceIsInterrupted() {
        var disposer = new Disposer();
        disposer.add(() -> {
            throw new InterruptedException("stop");
        });
        try {
            assertThatThrownBy(disposer::close).isInstanceOf(Disposer.DisposeException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }
}
