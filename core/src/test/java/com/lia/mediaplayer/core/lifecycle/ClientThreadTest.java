package com.lia.mediaplayer.core.lifecycle;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ClientThreadTest {

    private static ClientThread answering(boolean current) {
        return new ClientThread() {
            @Override
            public void execute(Runnable task) {
                task.run();
            }

            @Override
            public boolean isCurrent() {
                return current;
            }
        };
    }

    @Test
    void checkCurrentPassesOnTheClientThread() {
        assertThatCode(() -> answering(true).checkCurrent()).doesNotThrowAnyException();
    }

    @Test
    void checkCurrentNamesTheOffendingThread() {
        assertThatThrownBy(() -> answering(false).checkCurrent())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(Thread.currentThread().getName());
    }
}
