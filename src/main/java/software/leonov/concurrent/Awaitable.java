package software.leonov.concurrent;

import java.time.Duration;

/**
 * A conditional abstraction which can be waited upon.
 * <p>
 * <b>Implementation Considerations</b>
 * <p>
 * Depending on the underlying platform, a <i>spurious wakeup</i> is permitted to occur, when a thread is
 * {@link #await() waiting} to be signalled. In general, application programmers should guard against spurious wakeups
 * by placing waiting threads in a loop which checks if the state being waited upon has changed.
 * <p>
 * All implementing classes <b><u>are encouraged</u></b> to remove the possibility of spurious wakeups in their internal
 * implementation. In either case, the exact semantics and guarantees should be clearly documented in their API.
 * 
 * @author Zhenya Leonov
 */
public interface Awaitable {

    /**
     * Causes the current thread to wait until it is signalled or {@link Thread#interrupt() interrupted}.
     *
     * @throws InterruptedException if the current thread is interrupted (and interruption of thread suspension is
     *                              supported)
     */
    public void await() throws InterruptedException;

    /**
     * Causes the current thread to wait until it is signalled, it is {@link Thread#interrupt() interrupted}, or the
     * specified waiting time elapses.
     *
     * @param duration the maximum time to wait
     * @return {@code false} if the waiting time elapsed or {@code true} if the thread was signalled
     * @throws ArithmeticException  if duration is too large to fit in a {@code long} nanoseconds value
     * @throws InterruptedException if the current thread is interrupted (and interruption of thread suspension is
     *                              supported)
     * @throws NullPointerException if {@code duration} is {@code null}
     */
    public boolean await(final Duration duration) throws InterruptedException;

    /**
     * Causes the current thread to wait indefinitely until it is signalled.
     * <p>
     * If the current thread is {@link Thread#interrupted() interrupted} while waiting, it will continue to wait until
     * signalled. When this method returns the thread's {@link Thread#isInterrupted() interrupted status} will still be set.
     */
    public void awaitUninterruptibly();

}
