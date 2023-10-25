package software.leonov.concurrent;

import static software.leonov.concurrent.TestUtilities.sleepUninterruptibly;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class BoundedExecutorServiceTest {

    private ThreadPoolExecutor executor;

    @Test
    void testThreads() throws InterruptedException {
        executor = (ThreadPoolExecutor) Executors.newCachedThreadPool();

        final BoundedExecutorService exec = new BoundedExecutorService(executor, 2);

        exec.execute(new Task());
        System.out.println(executor);
        exec.execute(new Task());
        System.out.println(executor);
        exec.execute(new Task());
        System.out.println(executor);
        exec.execute(new Task());
        System.out.println(executor);
        exec.execute(new Task());
        System.out.println(executor);
        exec.execute(new Task());
        System.out.println(executor);
        
        ExecutorServices.shutdownAndEnsureTermination(exec);
    }

    private static class Task implements Runnable {

        @Override
        public void run() {
            System.out.println(Thread.currentThread().getName() + ": sleeping");
            for (int i = 1; i <= 10; i++) {
                System.out.println(Thread.currentThread().getName() + ": " + i);
                sleepUninterruptibly(TimeUnit.SECONDS.toMillis(1));
            }
        }
    }

}
