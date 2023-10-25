package software.leonov.concurrent;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.util.concurrent.TimeUnit;

public final class TestUtilities {

    private TestUtilities() {
    }

    public static void sleepUninterruptibly(final long millis) {
        boolean interrupted = false;

        try {
            long remaining = TimeUnit.MILLISECONDS.toNanos(millis);
            long end = System.nanoTime() + remaining;
            while (true) {
                try {
                    NANOSECONDS.sleep(remaining);
                    return;
                } catch (final InterruptedException e) {
                    interrupted = true;
                    remaining = end - System.nanoTime();
                }
            }
        } finally {
            if (interrupted)
                Thread.currentThread().interrupt();
        }
    }

}