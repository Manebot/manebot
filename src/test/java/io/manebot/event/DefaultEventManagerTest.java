package io.manebot.event;

import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

public class DefaultEventManagerTest {

    @Test
    public void testExecute() {
        DefaultEventManager eventManager = new DefaultEventManager();
        TestEvent event = new TestEvent(this);
        final boolean[] accepted = new boolean[1];

        eventManager.registerListener(new TestListener(fired -> {
            accepted[0] = true;
            assertEquals(event, fired);
        }));

        eventManager.execute(event);
        assertTrue(accepted[0]);
    }

    @Test
    public void testExecuteAsync() throws ExecutionException, InterruptedException {
        DefaultEventManager eventManager = new DefaultEventManager();
        TestEvent event = new TestEvent(this);
        final boolean[] accepted = new boolean[1];

        eventManager.registerListener(new TestListener(fired -> {
            accepted[0] = true;
            assertEquals(event, fired);
        }));

        Future<TestEvent> future = eventManager.executeAsync(event);

        // Await event
        future.get();
        assertTrue(accepted[0]);
    }

    private static class TestListener implements EventListener {
        private final Consumer<TestEvent> eventConsumer;

        private TestListener(Consumer<TestEvent> eventConsumer) {
            this.eventConsumer = eventConsumer;
        }

        @EventHandler
        public void onTestEvent(TestEvent event) {
            eventConsumer.accept(event);
        }
    }

    private static class TestEvent extends Event {
        public TestEvent(Object sender) {
            super(sender);
        }
    }

}
