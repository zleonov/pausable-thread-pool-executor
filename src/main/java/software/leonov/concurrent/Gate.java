package software.leonov.concurrent;

import static java.util.Objects.requireNonNull;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * A boolean synchronization mechanism with semantics similar to {@link CountDownLatch#CountDownLatch(int) new
 * CountDownLatch(1)}, offering more intuitive naming conventions, and additional functionality.
 * <p>
 * A {@code Gate} is {@link #isOpen() open} or closed. Threads can {@link #await() wait} for a closed gate to open or
 * {@link #guard()} an open gate until it closes.
 */
public final class Gate {

    private final static int OPEN = 0;
    private final static int CLOSED = 1;

    @SuppressWarnings("serial")
    private static class Sync extends AbstractQueuedSynchronizer {

        Sync(final int state) {
            setState(state);
        }

        int state() {
            return getState();
        }

        protected int tryAcquireShared(final int state) {
            return getState() == state ? 1 : -1;
        }

        protected boolean tryReleaseShared(final int state) {
            while (true) {
                int current = getState();

                if (current == state)
                    return false;

                if (compareAndSetState(current, state))
                    return true;
            }
        }
    }

    private final Sync sync;

    private Gate(final int state) {
        this.sync = new Sync(state);
    }

    /**
     * Creates and returns a new open gate.
     *
     * @return a new open gate
     */
    public static Gate createOpen() {
        return new Gate(OPEN);
    }

    /**
     * Creates and returns a new closed gate.
     *
     * @return a new closed gate
     */
    public static Gate createClosed() {
        return new Gate(CLOSED);
    }

    /**
     * Checks whether the gate is open.
     *
     * @return {@code true} if the gate is open or {@code false} otherwise
     */
    public boolean isOpen() {
        return sync.state() == OPEN;
    }

    /**
     * Causes the current thread to wait until the gate is open.
     *
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public void await() throws InterruptedException {
        sync.acquireSharedInterruptibly(OPEN);
    }

    /**
     * Causes the current thread to wait until the gate is open or the waiting time elapsed.
     *
     * @param timeout the maximum time to wait
     * @return {@code true} the gate was opened or {@code false} if the waiting time elapsed
     * @throws ArithmeticException  if duration is too large to fit in a {@code long} nanoseconds value
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public boolean await(final Duration timeout) throws InterruptedException {
        requireNonNull(timeout, "timeout == null");
        return sync.tryAcquireSharedNanos(OPEN, timeout.toNanos());
    }

    /**
     * Causes the current thread to wait until the gate is closed.
     *
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public void guard() throws InterruptedException {
        sync.acquireSharedInterruptibly(CLOSED);
    }

    /**
     * Causes the current thread to wait until the gate is closed or the waiting time elapsed.
     *
     * @param timeout the maximum time to wait
     * @return {@code true} the gate was closed or {@code false} if the waiting time elapsed
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public boolean guard(final Duration timeout) throws InterruptedException {
        return sync.tryAcquireSharedNanos(CLOSED, timeout.toNanos());
    }

    /**
     * Opens the gate. If the gate is already open this method has no effect.
     */
    public void open() {
        sync.releaseShared(OPEN);
    }

    /**
     * Closes the gate. If the gate is already closed this method has no effect.
     */
    public void close() {
        sync.releaseShared(CLOSED);
    }

}