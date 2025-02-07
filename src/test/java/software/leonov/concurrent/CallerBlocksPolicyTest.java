package software.leonov.concurrent;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import software.leonov.concurrent.PausableThreadPoolExecutor.CallerBlocksPolicy;

class CallerBlocksPolicyTest {

    private static Duration sleepTimeout = Duration.ofSeconds(5);

    @BeforeEach
    void beforeEach() {
    }

    @AfterEach
    void afterEach() throws InterruptedException {
    }

    @Test
    void test_shutdown() throws InterruptedException {
        PausableThreadPoolExecutor exec = PausableThreadPoolExecutor.newThreadPool(1, 1, Duration.ZERO, new ArrayBlockingQueue<>(1))
                .setRejectedExecutionHandler(
                        new CallerBlocksPolicy(
                                () -> System.out.println(Thread.currentThread().getName() + ": Waiting for space to become available in the work queue"),
                                () -> System.out.println(Thread.currentThread().getName() + ": Task submitted"))
                        
                        
//                        new AbortPolicy()
                        )
                .create();

        AtomicInteger n = new AtomicInteger(1);
        BlockingQueue<Runnable> queue = exec.getQueue();
        
        System.out.println(Thread.currentThread().getName() + ": Submitting Task-" + n);
        exec.execute(new Task(n.getAndIncrement()));
        System.out.println(Thread.currentThread().getName() + ": Submitting Task-" + n);
        exec.execute(new Task(n.getAndIncrement()));
        
        //System.out.println(exec.getQueue().remainingCapacity());

        

        Thread t = new Thread(() -> {
            try {
                System.out.println(Thread.currentThread().getName() + ": Submitting Task-" + n);
            exec.submit(new Task(n.getAndIncrement()));
            } catch (Throwable err) {
                err.printStackTrace();
            }
        });
        
        t.start();
        
        sleep(100);

        ExecutorServices.shutdownNowAndAwaitTermination(exec);
        
        System.out.println("Queue size after shutdownNow request: " + queue.size());
        
        sleep(10000);
        
        if(t.isAlive())
            t.interrupt();
    }

    private static class Task implements Runnable {
        private final int n;

        Task(final int n) {
            this.n = n;
        }

        @Override
        public void run() {
            final String name = Thread.currentThread().getName();
            System.out.println(name + ": Task-" + n + " Starting");
            System.out.println(name + ": Task-" + n + " Sleeping for " + sleepTimeout.toMillis() + " millis");
            sleep(sleepTimeout.toMillis());
            System.out.println(name + ": Task-" + n + " Done");
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
