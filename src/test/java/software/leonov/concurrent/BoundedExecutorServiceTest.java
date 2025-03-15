package software.leonov.concurrent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.IntSummaryStatistics;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BoundedExecutorServiceTest {

    private static AtomicInteger          count;
    private static int                    ntasks;
    private static IntSummaryStatistics   stats;
    private static BoundedExecutorService exec;

    @BeforeEach
    void beforeEach() {
        ntasks = 5;
        count  = new AtomicInteger();
        stats  = new IntSummaryStatistics();
        exec   = new BoundedExecutorService(Executors.newCachedThreadPool(), ntasks);
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        Execution.shutdownAndAwaitTermination(exec);
    }

    @Test
    void test_max_tasks() throws InterruptedException {

        for (int i = 0; i < ThreadLocalRandom.current().nextInt(ntasks, ntasks * 50); i++)
            exec.submit(new Task());

        assertEquals(ntasks, stats.getMax());
    }

    private static class Task implements Runnable {

        @Override
        public void run() {
            int n = count.addAndGet(1);
            stats.accept(n);
            long millis = ThreadLocalRandom.current().nextLong(100, 1000);
            System.out.println(Thread.currentThread().getName() + " sleeping for " + millis + "ms [" + "ntasks = " + n + "]");
            sleep(millis);
            count.decrementAndGet();
        }
    }

    private static void sleep(final long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
