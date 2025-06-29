package software.leonov.concurrent;

import static java.util.Objects.requireNonNull;

import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.Semaphore;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A {@code ThreadPoolExecutor} that processes tasks using a fixed number of threads.
 *
 * <p>
 * Unlike a traditional {@link Executors#newFixedThreadPool(int) fixed-thread-pool}, a {@code BoundedThreadPoolExecutor}
 * will blocking the calling thread if new tasks are submitted when all threads are busy, until a thread becomes
 * available to process the new task.
 * </ul>
 * 
 * @author Zhenya Leonov
 */
public final class BoundedThreadPoolExecutor extends ThreadPoolExecutor {

    private final Semaphore semaphore;
    private final int       nthreads;

    public BoundedThreadPoolExecutor(final int nthreads) {
        super(nthreads, nthreads, 0L, TimeUnit.MILLISECONDS, new SynchronousQueue<>());

        if (nthreads < 1)
            throw new IllegalArgumentException("nthreads < 1");

        this.semaphore = new Semaphore(nthreads);
        this.nthreads  = nthreads;
    }

    public BoundedThreadPoolExecutor(final int nthreads, final ThreadFactory factory) {
        super(nthreads, nthreads, 0L, TimeUnit.MILLISECONDS, new SynchronousQueue<>(), requireNonNull(factory, "factory == null"));

        this.semaphore = new Semaphore(nthreads);
        this.nthreads  = nthreads;
    }

    @Override
    public void execute(final Runnable command) {
        requireNonNull(command, "command == null");

        try {
            semaphore.acquire();
            super.execute(() -> {
                try {
                    command.run();
                } finally {
                    semaphore.release();
                }
            });
        } catch (final RejectedExecutionException e) {
            semaphore.release();
            throw e;
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * This operation is not supported.
     * 
     * @throws UnsupportedOperationException always
     */
    public void setRejectedExecutionHandler(final RejectedExecutionHandler handler) {
        throw new UnsupportedOperationException();
    }

    /**
     * This operation is not supported.
     * 
     * @throws UnsupportedOperationException always
     */
    public void setCorePoolSize(final int corePoolSize) {
        throw new UnsupportedOperationException();
    }

    /**
     * This operation is not supported.
     * 
     * @throws UnsupportedOperationException always
     */
    public void setMaximumPoolSize(final int maximumPoolSize) {
        throw new UnsupportedOperationException();
    }

    /**
     * This operation is not supported.
     * 
     * @throws UnsupportedOperationException always
     */
    public void setKeepAliveTime(final long time, final TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "[" + super.toString() + ", bound = " + nthreads + "]";
    }

}