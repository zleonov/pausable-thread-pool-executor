package software.leonov.concurrent;

import static software.leonov.concurrent.UninterruptibleThread.runUninterruptibly;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Test {

    public Test() {
        // TODO Auto-generated constructor stub
    }

    public static void main(String[] args) throws InterruptedException {
        ThreadPoolExecutor exec = (ThreadPoolExecutor) Executors.newFixedThreadPool(2, ExecutorServices.uninterruptibleThreadFactory());

        final AtomicInteger task = new AtomicInteger();

        Runnable r = () -> {
            final String thread = Thread.currentThread().getName();
            final int count = task.incrementAndGet();

            System.out.println(thread + ": Task-" + count + " starting");

            System.out.println(thread + ": Task-" + count + " sleeping");
            
            runUninterruptibly(() -> {
                TimeUnit.SECONDS.sleep(6);
            });

            try {
                TimeUnit.SECONDS.sleep(6);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            System.out.println(thread + ": Task-" + count + " finished");

            System.out.println(thread + ": has " + (Thread.currentThread().isInterrupted() ? "been interrupted" : "not been interrupted"));
        };

        for (int i = 0; i < 5; i++)
            exec.execute(r);

        TimeUnit.SECONDS.sleep(3);

        // ExecutorServices.shutdownAndEnsureTermination(pool);
        ExecutorServices.shutdownNowAndEnsureTermination(exec);
    }

}
