package software.leonov.concurrent;

import static software.leonov.concurrent.UninterruptibleThread.disableInterruption;
import static software.leonov.concurrent.UninterruptibleThread.enableInterruption;

import java.util.concurrent.TimeUnit;

public class Test3 {

    public static void main(String[] args) throws Exception {

        final UninterruptibleThread thread = new UninterruptibleThread(() -> {
            disableInterruption();
                for (int i = 0; i < 10; i++) {
                    System.out.print(i);
                    try {
                        TimeUnit.SECONDS.sleep(1);
                    } catch (InterruptedException e) { // cannot happen
                    }
                }
            enableInterruption();
            System.out.println(Thread.currentThread().isInterrupted());
        });

        thread.start();
        TimeUnit.SECONDS.timedJoin(thread, 1);
        thread.interrupt();
        thread.join();

    }

}
