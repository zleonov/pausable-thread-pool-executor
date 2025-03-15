package software.leonov.concurrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.Test;

class ThreadPerTaskExecutorTest {

    private ThreadPerTaskExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new ThreadPerTaskExecutor();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdown(true);
        executor.awaitTermination();
    }

    @Test
    void test_execute_single_task() throws InterruptedException {
        final AtomicBoolean ran = new AtomicBoolean(false);
        executor.execute(() -> ran.set(true));

        executor.shutdown(false);
        executor.awaitTermination();

        assertTrue(ran.get());
    }

    @Test
    void test_execute_rejectedExecution_after_shutdown() {
        executor.shutdown(false);
        assertThrows(RejectedExecutionException.class, () -> executor.execute(() -> {
        }));
    }

    @Test
    void test_execute_null_runnable() {
        assertThrows(NullPointerException.class, () -> executor.execute(null));
    }

    @Test
    void test_shutdown_no_tasks_no_interrupt() throws InterruptedException {
        executor.shutdown(false);
        assertTrue(executor.isShutdown());
        assertTrue(executor.isTerminated());
    }

    @Test
    void test_shutdown_with_interrupt() throws InterruptedException {
        final AtomicBoolean interrupted = new AtomicBoolean(false);
        final BooleanLatch  latch       = new BooleanLatch();

        executor.execute(() -> {
            try {
                latch.await();
            } catch (InterruptedException e) {
                interrupted.set(true);
            }
        });

        executor.shutdown(true);
        executor.awaitTermination();

        assertTrue(interrupted.get());
    }

    @Test
    void test_shutdown_with_interruption() throws InterruptedException {
        final AtomicBoolean interrupted = new AtomicBoolean(false);
        final BooleanLatch  latch       = new BooleanLatch();

        executor.execute(() -> {
            latch.awaitUninterruptibly();
            if (Thread.interrupted())
                interrupted.set(true);
        });

        executor.shutdown(true);
        latch.signal();
        executor.awaitTermination();

        assertTrue(interrupted.get());
    }

    @Test
    void test_isShutdown_initial_state() {
        assertFalse(executor.isShutdown());
    }

    @Test
    void test_isTerminated_initial_state() {
        assertFalse(executor.isTerminated());
    }

    @Test
    void test_awaitTermination_with_timeout_terminated() throws InterruptedException {
        ThreadPerTaskExecutor executor = new ThreadPerTaskExecutor();
        executor.shutdown(false);
        assertTrue(executor.awaitTermination(Duration.ofMillis(100)));
    }

    @Test
    void testAwaitTermination_withTimeout_notTerminated() throws InterruptedException {
        ThreadPerTaskExecutor executor = new ThreadPerTaskExecutor();
        executor.execute(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        executor.shutdown(false);
        assertFalse(executor.awaitTermination(Duration.ofMillis(100)));
    }

    @Test
    void test_awaitTermination_no_tasks_terminated() throws InterruptedException {
        executor.shutdown(false);
        executor.awaitTermination();
        assertTrue(executor.isTerminated());
    }

    @Test
    void test_awaitTermination_no_timeout_interrupted() throws InterruptedException {

        final BooleanLatch latch = new BooleanLatch();

        executor.execute(() -> {
            latch.awaitUninterruptibly();
        });

        executor.shutdown(false);

        final Thread t = new Thread(() -> {
            try {
                executor.awaitTermination();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            latch.awaitUninterruptibly();
        });

        t.start();
        t.interrupt();

        assertTrue(t.isInterrupted());

        latch.signal();
    }

    @RepeatedTest(10)
    void test_toString_running_with_active_tasks(final RepetitionInfo repetition) {
        final int          n       = repetition.getCurrentRepetition();
        final BooleanLatch latch   = new BooleanLatch();
        final Counter      counter = new Counter(n);

        for (int i = 0; i < n; i++) {
            executor.execute(() -> {
                counter.countDown();
                latch.awaitUninterruptibly();
            });
        }

        counter.awaitUninterruptibly();

        assertEquals("ThreadPerTaskExecutor [state = RUNNING, activeCount = " + n + "]", executor.toString());

        latch.signal();
    }

    @RepeatedTest(10)
    void test_toString_shutting_down_with_active_tasks(final RepetitionInfo repetition) throws InterruptedException {
        final int          ntasks  = repetition.getCurrentRepetition();
        final BooleanLatch latch   = new BooleanLatch();
        final Counter      counter = new Counter(ntasks);

        for (int i = 0; i < ntasks; i++) {
            executor.execute(() -> {
                counter.countDown();
                latch.awaitUninterruptibly();
            });
        }

        counter.awaitUninterruptibly();

        executor.shutdown(false);

        final String actual = executor.toString();

        latch.signal();

        assertEquals("ThreadPerTaskExecutor [state = SHUTDOWN, activeCount = " + ntasks + "]", actual);
    }

    @RepeatedTest(10)
    void test_getActiveCount(final RepetitionInfo repetition) throws InterruptedException {
        final int          n       = repetition.getCurrentRepetition();
        final BooleanLatch latch   = new BooleanLatch();
        final Counter      counter = new Counter(n);

        for (int i = 0; i < n; i++) {
            executor.execute(() -> {
                counter.countDown();
                latch.awaitUninterruptibly();
            });
        }

        counter.awaitUninterruptibly();

        assertEquals(n, executor.getActiveCount());

        latch.signal();
    }

    @Test
    void testToString_shuttingDown() {
        ThreadPerTaskExecutor executor = new ThreadPerTaskExecutor();
        executor.shutdown(false);
        String result = executor.toString();
        assertTrue(result.contains("TERMINATED"));
    }

    @Test
    void testToString_terminated() throws InterruptedException {
        ThreadPerTaskExecutor executor = new ThreadPerTaskExecutor();
        executor.shutdown(false);
        executor.awaitTermination();
        String result = executor.toString();
        assertTrue(result.contains("TERMINATED"));
    }

    @Test
    void testCustomThreadFactory() throws InterruptedException {
        AtomicBoolean         threadCreated = new AtomicBoolean(false);
        ThreadFactory         customFactory = r -> {
                                                threadCreated.set(true);
                                                return new Thread(r);
                                            };
        ThreadPerTaskExecutor executor      = new ThreadPerTaskExecutor(customFactory);
        executor.execute(() -> {
        });
        Thread.sleep(100);
        assertTrue(threadCreated.get());
    }

    @Test
    void test_shutdown_multiple_calls_no_op() throws InterruptedException {
        ThreadPerTaskExecutor executor = new ThreadPerTaskExecutor();
        executor.shutdown();
        executor.shutdown();
        executor.shutdown();
        executor.shutdown();
        executor.shutdown();
        executor.shutdown();
        assertTrue(executor.isShutdown());
        assertTrue(executor.isTerminated());
    }
}