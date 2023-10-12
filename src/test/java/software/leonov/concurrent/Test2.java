package software.leonov.concurrent;

import static software.leonov.concurrent.UninterruptibleThread.runUninterruptibly;

import java.util.concurrent.TimeUnit;

public class Test2 {

    public static void main(String[] args) throws Exception {

        final UninterruptibleThread thread = new UninterruptibleThread(() -> {
            runUninterruptibly(() -> {
                for (int i = 0; i < 10; i++) {
                    System.out.print(i);
                    TimeUnit.SECONDS.sleep(1);
                   // runUninterruptibly(() -> Thread.currentThread().interrupt());
                }
            });
            System.out.println(Thread.currentThread().isInterrupted());
        });

        thread.start();
        TimeUnit.SECONDS.timedJoin(thread, 1);
        thread.interrupt();
        thread.join();

    }

}
