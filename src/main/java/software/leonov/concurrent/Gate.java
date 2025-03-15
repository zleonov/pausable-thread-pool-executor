package software.leonov.concurrent;

import static java.util.Objects.requireNonNull;

import java.time.Duration;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * A boolean synchronization aid with semantics similar to {@link BooleanLatch} that allows threads to wait for state
 * transitions in both directions.
 * <p>
 * A {@code Gate} is either {@link #isOpen() open} or closed. Threads can {@link #await() wait} for a closed gate to
 * open or {@link #guard()} an open gate until it closes.
 * 
 * @implNote This class manages the possibility of <i>spurious wakeups</i> internally.
 * @author Zhenya Leonov
 */
public final class Gate implements Awaitable {

    private final static int OPEN = 0;
    private final static int CLOSED = 1;

    private static class Synchronizer extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = -3204469639067561686L;

        Synchronizer(final int value) {
            setState(value);
        }

        int state() {
            return getState();
        }

        protected int tryAcquireShared(final int value) {
            return getState() == value ? 1 : -1;
        }

        protected boolean tryReleaseShared(final int updatedCount) {
            while (true) {
                int expectedCount = getState();

                if (expectedCount == updatedCount)
                    return false;

                if (compareAndSetState(expectedCount, updatedCount))
                    return true;
            }
        }
    }

    private final Synchronizer sync;

    private Gate(final int state) {
        this.sync = new Synchronizer(state);
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
     * Causes the current thread to wait until the gate is open or the thread is {@link Thread#interrupt interrupted}.
     */
    @Override
    public void await() throws InterruptedException {
        sync.acquireSharedInterruptibly(OPEN);
    }

    /**
     * Causes the current thread to wait until the gate is open, the thread is {@link Thread#interrupt interrupted}, or the
     * specified waiting time elapses.
     */
    @Override
    public boolean await(final Duration duration) throws InterruptedException, NullPointerException, ArithmeticException {
        requireNonNull(duration, "duration == null");
        return sync.tryAcquireSharedNanos(OPEN, duration.toNanos());
    }

    /**
     * Causes the current thread to wait indefinitely until the gate is open.
     * <p>
     * If the current thread is {@link Thread#interrupted() interrupted} while waiting, it will continue to wait until the
     * gate is open. When this method returns the thread's {@link Thread#isInterrupted() interrupted status} will still be
     * set.
     */
    @Override
    public void awaitUninterruptibly() {
        sync.acquireShared(OPEN);
    }

    /**
     * Causes the current thread to wait until the gate is closed or the thread is {@link Thread#interrupt interrupted}.
     *
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public void guard() throws InterruptedException {
        sync.acquireSharedInterruptibly(CLOSED);
    }

    /**
     * Causes the current thread to wait until the gate is closed, the thread is {@link Thread#interrupt interrupted}, or
     * the specified waiting time elapses.
     *
     * @param timeout the maximum time to wait
     * @return {@code true} the gate was closed or {@code false} if the waiting time elapsed
     * @throws ArithmeticException  if duration is too large to fit in a {@code long} nanoseconds value
     * @throws InterruptedException if the current thread is interrupted (and interruption of thread suspension is
     *                              supported)
     * @throws NullPointerException if {@code duration} is {@code null}
     */
    public boolean guard(final Duration duration) throws InterruptedException, NullPointerException, ArithmeticException {
        return sync.tryAcquireSharedNanos(CLOSED, duration.toNanos());
    }

    /**
     * Causes the current thread to wait indefinitely until the gate is closed.
     * <p>
     * If the current thread is {@link Thread#interrupted() interrupted} while waiting, it will continue to wait until the
     * gate is closed. When this method returns the thread's {@link Thread#isInterrupted() interrupted status} will still be
     * set.
     */
    public void guardUninterruptibly() {
        sync.acquireShared(CLOSED);
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

    @Override
    public String toString() {
        return Counter.class.getSimpleName() + " [gate is " + (isOpen() ? "open" : "closed") + "]";
    }

}