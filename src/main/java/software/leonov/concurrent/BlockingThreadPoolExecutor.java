package software.leonov.concurrent;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A {@code ThreadPoolExecutor} that maintains a fixed number of threads and blocks callers when at capacity.
 * <p>
 * Unlike a traditional {@link Executors#newFixedThreadPool(int) fixed thread pool} which queues unlimited tasks, a
 * {@link BlockingThreadPoolExecutor} blocks the submitting thread if all pooled threads are busy. The caller remains
 * blocked until a thread becomes available to process the new task. This approach prevents memory exhaustion from
 * unbounded task queues while providing automatic flow control through blocking semantics.
 * <p>
 * This thread pool is not configurable. The {@link #setCorePoolSize(int)}, {@link #setKeepAliveTime(long, TimeUnit)},
 * {@link #setMaximumPoolSize(int)}, {@link #setRejectedExecutionHandler(RejectedExecutionHandler)}, and
 * {@link #setThreadFactory(ThreadFactory)} methods are disabled. As long as this thread pool is active it will never
 * reject tasks.
 * 
 * @author Zhenya Leonov
 */
public final class BlockingThreadPoolExecutor extends ThreadPoolExecutor {

    private final Semaphore semaphore;
    private final int       nthreads;
    private Counter         counter = new Counter(0);

//    private class Task<V> extends FutureTask<V> {
//
//        public Task(final Callable<V> callable) {
//            super(callable);
//        }
//
//        public Task(final Runnable runnable, final V result) {
//            super(runnable, result);
//        }
//
//        protected void done() {
//            counter.countDown();
//            semaphore.release();
//        }
//    }
//
//    @Override
//    protected <T> RunnableFuture<T> newTaskFor(final Callable<T> callable) {
//        return new Task<T>(callable);
//    }
//
//    @Override
//    protected <T> RunnableFuture<T> newTaskFor(final Runnable runnable, final T value) {
//        return new Task<T>(runnable, value);
//    }

    /**
     * Creates a new {@link BlockingThreadPoolExecutor} which executes tasks using a fixed number of threads.
     * 
     * @param nthreads the number of threads in the pool
     */
    public BlockingThreadPoolExecutor(final int nthreads) {
        super(nthreads, nthreads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());

        if (nthreads < 1)
            throw new IllegalArgumentException("nthreads < 1");

        this.semaphore = new Semaphore(nthreads);
        this.nthreads  = nthreads;
    }

    /**
     * Creates a new {@link BlockingThreadPoolExecutor} which executes tasks using a fixed number of threads.
     * 
     * @param nthreads the number of threads in the pool
     * @param factory  the factory to use when creating new threads
     */
    public BlockingThreadPoolExecutor(final int nthreads, final ThreadFactory factory) {
        super(nthreads, nthreads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), requireNonNull(factory, "factory == null"));

        this.semaphore = new Semaphore(nthreads);
        this.nthreads  = nthreads;
    }

    /**
     * Executes the given task in one of the pooled threads. If all threads are busy the caller will block until a thread
     * becomes available.
     * <p>
     * This method does not throw {@link InterruptedException}s. If the caller thread is interrupted while waiting this
     * method will return immediately having the thread's interrupt status set.
     */
    @Override
    public void execute(final Runnable command) {
        requireNonNull(command, "command == null");

        try {
            semaphore.acquire();
            counter.countUp();
            super.execute(() -> {
                try {
                    command.run();
                } finally {
                    counter.countDown();
                    semaphore.release();
                }
            });
        } catch (final RejectedExecutionException e) {
            counter.countDown();
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
    @Override
    public void setRejectedExecutionHandler(final RejectedExecutionHandler handler) {
        throw new UnsupportedOperationException();
    }

    /**
     * This operation is not supported.
     * 
     * @throws UnsupportedOperationException always
     */
    @Override
    public void setCorePoolSize(final int corePoolSize) {
        throw new UnsupportedOperationException();
    }

    /**
     * This operation is not supported.
     * 
     * @throws UnsupportedOperationException always
     */
    @Override
    public void setMaximumPoolSize(final int maximumPoolSize) {
        throw new UnsupportedOperationException();
    }

    /**
     * This operation is not supported.
     * 
     * @throws UnsupportedOperationException always
     */
    @Override
    public void setKeepAliveTime(final long time, final TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    /**
     * This operation is not supported.
     * 
     * @throws UnsupportedOperationException always
     */
    @Override
    public void setThreadFactory(ThreadFactory threadFactory) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Runnable> shutdownNow() {
        final List<Runnable> tasks = super.shutdownNow();
        for (int i = 0; i < tasks.size(); i++) {
            counter.countDown();
            semaphore.release();
        }
        return tasks;
    }

    public void await() throws InterruptedException {
        counter.await();
    }

    /**
     * This operation is not supported.
     * 
     * @throws UnsupportedOperationException always
     */
    @Override
    public BlockingQueue<Runnable> getQueue() {
        throw new UnsupportedOperationException();
    }

    /**
     * This operation is not supported.
     * 
     * @throws UnsupportedOperationException always
     */
    @Override
    public boolean remove(Runnable task) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        final String parent = super.toString();
        return parent.substring(0, parent.length() - 1) + ", bound = " + nthreads + "]";
    }

}