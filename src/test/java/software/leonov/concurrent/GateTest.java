package software.leonov.concurrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static software.leonov.concurrent.TestUtilities.sleepUninterruptibly;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GateTest {

    private static final int DELTA = 50;
    private static final long ONE_SECOND_MILLIS = 1000;
    private static final long HALF_SECOND_MILLIS = 500;
    private static final Duration HALF_SECOND_DURATION = Duration.ofMillis(500);

    private Gate openGate;
    private Gate closedGate;

    @BeforeEach
    void setUp() {
        openGate = Gate.createOpen();
        closedGate = Gate.createClosed();
    }

    @Test
    void testCreateOpenGate() {
        assertTrue(openGate.isOpen());
    }

    @Test
    void testCreateClosedGate() {
        assertFalse(closedGate.isOpen());
    }

    @Test
    void testAwait() throws InterruptedException {

        new Thread(() -> {
            sleepUninterruptibly(ONE_SECOND_MILLIS);
            closedGate.open();
        }).start();

        final long start = System.currentTimeMillis();
        closedGate.await();
        final long end = System.currentTimeMillis();
        final long elapsedTime = end - start;

        assertTrue(closedGate.isOpen());
        assertEquals(elapsedTime, ONE_SECOND_MILLIS, DELTA);
    }

    @Test
    void testAwaitWithTimeout() throws InterruptedException {
        final long start = System.currentTimeMillis();
        final boolean result = closedGate.await(HALF_SECOND_DURATION);
        final long end = System.currentTimeMillis();
        final long elapsedTime = end - start;

        assertFalse(closedGate.isOpen());
        assertFalse(result);
        assertEquals(elapsedTime, HALF_SECOND_MILLIS, DELTA);
    }

    @Test
    void testGuard() throws InterruptedException {
        new Thread(() -> {
            sleepUninterruptibly(ONE_SECOND_MILLIS);
            openGate.close();
        }).start();

        final long start = System.currentTimeMillis();
        openGate.guard();
        final long end = System.currentTimeMillis();
        final long elapsedTime = end - start;

        assertFalse(openGate.isOpen());
        assertEquals(elapsedTime, ONE_SECOND_MILLIS, DELTA);
    }

    @Test
    void testGuardWithTimeout() throws InterruptedException {
        final long start = System.currentTimeMillis();
        final boolean result = openGate.guard(HALF_SECOND_DURATION);
        final long end = System.currentTimeMillis();
        final long elapsedTime = end - start;

        assertTrue(openGate.isOpen());
        assertFalse(result);
        assertEquals(elapsedTime, HALF_SECOND_MILLIS, DELTA);
    }

    @Test
    void testOpen() {
        openGate.open();
        assertTrue(openGate.isOpen());
    }

    @Test
    void testIsOpen() {
        openGate.open();
        assertTrue(openGate.isOpen());

        closedGate.close();
        assertFalse(closedGate.isOpen());
    }

    @Test
    void testClose() {
        openGate.close();
        assertFalse(openGate.isOpen());
    }

    @Test
    void testAwaitWithInterruption() {
        Thread.currentThread().interrupt();

        assertThrows(InterruptedException.class, () -> {
            closedGate.await();
        });
    }

    @Test
    void testGuardWithInterruption() {
        Thread.currentThread().interrupt();

        assertThrows(InterruptedException.class, () -> {
            openGate.guard();
        });
    }
}
