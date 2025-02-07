package software.leonov.concurrent;

import java.time.Duration;

/**
 * A conditional abstraction which can be waited upon.
 * <p>
 * <b>Implementation Considerations</b>
 * <p>
 * When a thread is {@link #await() waiting} to be signaled, a <i>spurious wakeup</i> is permitted to occur on most
 * platforms. In general, application programmers should guard against spurious wakeups by placing waiting logic in a
 * loop which checks if the state being waited upon has changed.
 * <p>
 * All implementing classes <b><u>are encouraged</u></b> to remove the possibility of spurious wakeups in their internal
 * implementation. In either case, the exact semantics and guarantees should be clearly documented in their API.
 * 
 * @author Zhenya Leonov
 */
public interface Awaitable {

    /**
     * Causes the current thread to wait until it is signaled or {@link Thread#interrupt() interrupted}.
     *
     * @throws InterruptedException if the current thread is interrupted (and interruption of thread suspension is
     *                              supported)
     */
    public void await() throws InterruptedException;

    /**
     * Causes the current thread to wait until it is signaled, {@link Thread#interrupt() interrupted}, or the specified
     * timeout elapses.
     *
     * @param duration the maximum time to wait
     * @return {@code true} if the thread was signaled or {@code false} if the timeout elapsed
     * @throws ArithmeticException  if duration is too large to fit in a {@code long} nanoseconds value
     * @throws InterruptedException if the current thread is interrupted while waiting
     * @throws NullPointerException if {@code duration} is {@code null}
     */
    public boolean await(final Duration duration) throws InterruptedException;

    /**
     * Causes the current thread to wait indefinitely until it is signaled.
     * <p>
     * If the current thread is {@link Thread#interrupted() interrupted} while waiting, when this method returns the
     * thread's {@link Thread#isInterrupted() interrupted status} will still be set to {@code true}.
     */
    public void awaitUninterruptibly();

}
