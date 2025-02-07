package software.leonov.concurrent;

import static java.util.Objects.requireNonNull;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * A synchronization aid that allows one or more threads to wait until a boolean condition is true. This latch
 * implementation is similar to {@link CountDownLatch#CountDownLatch(int) new CountDownLatch(1)} but operates on a
 * boolean state rather than a count, offering better naming conventions.
 * <p>
 * The latch is initialized in the false <i>unsignaled</i> state. Threads can wait for the latch to become true using
 * the {@code await} methods. When any thread sets the latch to true by invoking {@link #signal}, all waiting threads
 * are released.
 *
 * @implNote This class manages the possibility of <i>spurious wakeups</i> internally.
 * @author Zhenya Leonov
 */
public final class BooleanLatch implements Awaitable {

    private static class Synchronizer extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = 5231186218314040779L;

        Synchronizer() {
            setState(0);
        }

        boolean isSignaled() {
            return getState() == 1;
        }

        @Override
        protected int tryAcquireShared(final int value) {
            return getState() == 1 ? 1 : -1;
        }

        @Override
        protected boolean tryReleaseShared(final int value) {
            setState(1);
            return true;
        }
    }

    private final Synchronizer sync;

    public BooleanLatch() {
        this.sync = new Synchronizer();
    }

    /**
     * Causes the current thread to wait until the latch is {@link #signal() signaled} or the thread is
     * {@link Thread#interrupt() interrupted}. If the latch is already signaled this method returns immediately.
     * 
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    public void await() throws InterruptedException {
        sync.acquireSharedInterruptibly(1);
    }

    /**
     * Causes the current thread to wait until the latch is {@link #signal() signaled}, the thread is
     * {@link Thread#interrupt() interrupted}, or the specified timeout elapses. If the latch is already signaled this
     * method returns immediately.
     * 
     * @param duration the maximum time to wait
     * @return {@code true} if the latch is signaled or {@code false} if the timeout elapsed
     */
    @Override
    public boolean await(final Duration duration) throws InterruptedException, NullPointerException, ArithmeticException {
        requireNonNull(duration, "duration == null");
        return sync.tryAcquireSharedNanos(1, duration.toNanos());
    }

    /**
     * Signals the latch and releases all waiting threads. If the latch is already signaled this method is a no-op.
     */
    public void signal() {
        sync.releaseShared(1);
    }

    /**
     * Returns the current state of the latch.
     * 
     * @return {@code true} if the latch has be signaled or {@code false} otherwise
     */
    public boolean isSignaled() {
        return sync.isSignaled();
    }

    /**
     * Causes the current thread to wait indefinitely until the latch is {@link #signal() signaled}. If the latch is already
     * signaled this method returns immediately.
     * <p>
     * If the current thread is {@link Thread#interrupted() interrupted} while waiting the thread's
     * {@link Thread#isInterrupted() interrupted status} will still be set to {@code true} when this method returns.
     */
    @Override
    public void awaitUninterruptibly() {
        sync.tryAcquireShared(1);
    }

}