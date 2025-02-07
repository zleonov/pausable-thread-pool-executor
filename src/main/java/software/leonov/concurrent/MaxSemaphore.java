package software.leonov.concurrent;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * A <i>mock</i> {@link Semaphore} implementation that provides unlimited permits. This semaphore always reports
 * {@code Integer.MAX_VALUE} {@link #availablePermits() available permits} and performs no locking operations.
 * <p>
 * All {@code acquire} methods are non-blocking, {@link #getQueuedThreads() getQueuedThreads()} always returns an empty
 * collection, and {@link #hasQueuedThreads() hasQueuedThreads()} always returns {@code false}.
 * <p>
 * Particularly useful for testing scenarios and for simplifying code paths where semaphore-controlled access is only
 * required under certain conditions.
 *
 * @author Zhenya Leonov
 */
public final class MaxSemaphore extends Semaphore {

    private static final long serialVersionUID = 1L;

    private static final MaxSemaphore INSTANCE = new MaxSemaphore();

    private MaxSemaphore() {
        super(Integer.MAX_VALUE);
    }

    /**
     * Returns a singleton instance of this {@code Semaphore}.
     * 
     * @return a singleton instance of this {@code Semaphore}
     */
    public static MaxSemaphore getInstance() {
        return INSTANCE;
    }

    /**
     * This is a no-op.
     */
    @Override
    public void acquire() {
    }

    /**
     * This is a no-op.
     */
    @Override
    public void acquireUninterruptibly() {
    }

    /**
     * Always return {@code true}.
     * 
     * @return {@code true} - always
     */
    @Override
    public boolean tryAcquire() {
        return true;
    }

    /**
     * Always return {@code true}.
     * 
     * @return {@code true} - always
     */
    @Override
    public boolean tryAcquire(long timeout, TimeUnit unit) {
        return true;
    }

    /**
     * This is a no-op.
     */
    @Override
    public void release() {
    }

    /**
     * This is a no-op.
     */
    @Override
    public void acquire(int permits) {
    }

    /**
     * This is a no-op.
     */
    @Override
    public void acquireUninterruptibly(int permits) {
    }

    /**
     * Always return {@code true}.
     * 
     * @return {@code true} - always
     */
    @Override
    public boolean tryAcquire(int permits) {
        return true;
    }

    /**
     * Always return {@code true}.
     * 
     * @return {@code true} - always
     */
    @Override
    public boolean tryAcquire(int permits, long timeout, TimeUnit unit) {
        return true;
    }

    /**
     * This is a no-op.
     */
    @Override
    public void release(int permits) {
    }

    /**
     * Always return {@code Integer.MAX_VALUE}.
     * 
     * @return {@code Integer.MAX_VALUE}
     */
    @Override
    public int availablePermits() {
        return Integer.MAX_VALUE;
    }

    /**
     * Always return {@code Integer.MAX_VALUE}.
     * 
     * @return {@code Integer.MAX_VALUE}
     */
    @Override
    public int drainPermits() {
        return Integer.MAX_VALUE;
    }

    /**
     * Always return {@code true}.
     * 
     * @return {@code true} - always
     */
    @Override
    public boolean isFair() {
        return true;
    }

}
