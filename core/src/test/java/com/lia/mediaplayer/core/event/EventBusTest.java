package com.lia.mediaplayer.core.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.lia.mediaplayer.core.testing.FakePlatform;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class EventBusTest {

    private final FakePlatform platform = new FakePlatform(Path.of("."));
    private final EventBus bus = new EventBus(platform.clientThread());

    @Test
    void deliversOnTheClientThreadByTypeInOrder() {
        var strings = new ArrayList<String>();
        var numbers = new ArrayList<Number>();
        bus.subscribe(String.class, strings::add);
        bus.subscribe(Number.class, numbers::add);

        bus.post("a");
        bus.post(1);
        bus.post(2.5);
        bus.post("b");
        assertThat(strings).isEmpty();

        platform.runClientTasks();
        assertThat(strings).containsExactly("a", "b");
        assertThat(numbers).containsExactly(1, 2.5);
    }

    @Test
    void aFailingListenerKeepsItsSubscriptionAndOthersStillHear() {
        var heard = new ArrayList<String>();
        bus.subscribe(String.class, s -> {
            throw new IllegalStateException("listener bug");
        });
        bus.subscribe(String.class, heard::add);
        bus.post("x");
        bus.post("y");
        platform.runClientTasks();
        assertThat(heard).containsExactly("x", "y");
    }

    @Test
    void closingASubscriptionStopsDelivery() {
        var heard = new ArrayList<String>();
        var subscription = bus.subscribe(String.class, heard::add);
        bus.post("before");
        platform.runClientTasks();
        subscription.close();
        subscription.close();
        bus.post("after");
        platform.runClientTasks();
        assertThat(heard).containsExactly("before");
    }
}
