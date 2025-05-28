package software.leonov.concurrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static software.leonov.concurrent.TestUtilities.sleepUninterruptibly;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BooleanLatchTest {

    private static final int DELTA = 50;
    private static final long ONE_SECOND_MILLIS = 1000;
    private static final long HALF_SECOND_MILLIS = 500;
    private static final Duration HALF_SECOND_DURATION = Duration.ofMillis(500);

    private BooleanLatch latch;

    @BeforeEach
    void setUp() {
        latch = new BooleanLatch();
    }

    @Test
    void test_create_unsignaled() {
        assertFalse(latch.isSignaled());
    }

    @Test
    void test_await_with_timeout() throws InterruptedException {

        new Thread(() -> {
            sleepUninterruptibly(ONE_SECOND_MILLIS);
            latch.signal();
        }).start();

        final long start = System.currentTimeMillis();
        latch.await();
        final long end = System.currentTimeMillis();
        final long elapsedTime = end - start;

        assertTrue(latch.isSignaled());
        assertEquals(elapsedTime, ONE_SECOND_MILLIS, DELTA);
    }
    

}
