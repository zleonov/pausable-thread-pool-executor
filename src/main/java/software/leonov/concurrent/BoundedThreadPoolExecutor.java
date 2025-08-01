package software.leonov.concurrent;

import static java.util.Objects.requireNonNull;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * A {@code ThreadPoolExecutor} that maintains a fixed number of threads and blocks callers when at capacity.
 * <p>
 * Unlike a traditional {@link Executors#newFixedThreadPool(int) fixed thread pool} which queues unlimited tasks, a
 * {@link BoundedThreadPoolExecutor} blocks the submitting thread if all pooled threads are busy. The caller remains
 * blocked until a thread becomes available to process the new task. Essentially behaving as a <i>CallerBlocksPolicy</i>
 * {@link RejectedExecutionHandler}. This approach prevents memory exhaustion from unbounded task queues while providing
 * automatic flow control through blocking semantics.
 * <p>
 * This thread pool is not configurable. The {@link #setCorePoolSize(int)}, {@link #setKeepAliveTime(long, TimeUnit)},
 * {@link #setMaximumPoolSize(int)}, {@link #setRejectedExecutionHandler(RejectedExecutionHandler)},
 * {@link #setThreadFactory(ThreadFactory)}, {@link #getQueue()}, and {@link #remove(Runnable)} methods are disabled. As
 * long as this thread pool is active it will never reject tasks.
 * <p>
 * <b>Additional functionality</b> <br>
 * In addition this class provides a new {@link #await()} method with novel functionality to wait for submitted tasks to
 * complete execution.
 * 
 * @author Zhenya Leonov
 */
public final class BoundedThreadPoolExecutor extends ThreadPoolExecutor {

    private final BoundedExecutorService svc;

    /**
     * Creates a new {@link BoundedThreadPoolExecutor} which executes tasks using a fixed number of threads.
     * 
     * @param nthreads the number of threads in the pool
     */
    public BoundedThreadPoolExecutor(final int nthreads) {
        this(nthreads, Executors.defaultThreadFactory());
    }

    /**
     * Creates a new {@link BoundedThreadPoolExecutor} which executes tasks using a fixed number of threads.
     * 
     * @param nthreads the number of threads in the pool
     * @param factory  the factory to use when creating new threads
     */
    public BoundedThreadPoolExecutor(final int nthreads, final ThreadFactory factory) {
        super(((Supplier<Integer>) () -> {
            if (nthreads < 1)
                throw new IllegalArgumentException("nthreads < 1");
            return nthreads;
        }).get(), nthreads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), requireNonNull(factory, "factory == null"));

        svc = new BoundedExecutorService(this, nthreads);
    }

    /**
     * Executes the given task in one of the pooled threads. If all threads are busy the caller will block until a thread
     * becomes available.
     * <p>
     * This method does not throw {@link InterruptedException}s. If the calling thread is interrupted while waiting this
     * method will return immediately having the thread's interrupt status set.
     */
    @Override
    public void execute(final Runnable command) {
        svc.execute(command);
    }

    void _execute(final Runnable command) {
        super.execute(command);
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
        return svc.shutdownNow();
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

    BlockingQueue<Runnable> _getQueue() {
        return super.getQueue();
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

    /**
     * Waits for all currently submitted tasks to complete execution.
     * <p>
     * Provides a blocking mechanism that suspends the calling thread until all tasks that have been submitted to this
     * executor via {@link #execute(Runnable) execute} or its various {@code submit} methods have finished processing. The
     * executor remains fully operational after this method returns.
     * <p>
     * <b>Motivation and Design Rationale</b>
     * <p>
     * Standard {@link ExecutorService} implementations provide several mechanisms for waiting on task completion, but each
     * comes with limitations that make them undesirable for certain use cases:
     * <p>
     * <table border="1" cellpadding="5">
     * <tr>
     * <th>Approach</th>
     * <th>Limitations</th>
     * </tr>
     * <tr>
     * <td>Call {@link Future#get()} on each {@link Future} returned from task submission</td>
     * <td>
     * <ul>
     * <li>Doesn't work with {@link #execute(Runnable)} which does not return a {@code Future}</li>
     * <li>Requires unnecessary managing and storing of individual {@code Future} objects for {@code Runnable} tasks</li>
     * <li>Forces error handling at the point of waiting in case {@code Future.get()} results in an exception</li>
     * </ul>
     * </td>
     * </tr>
     * <tr>
     * <td>Submit all tasks at once and wait for completion using {@link ExecutorService#invokeAll(Collection)
     * invokeAll}</td>
     * <td>
     * <ul>
     * <li>Requires all tasks to be available upfront</li>
     * <li>Only works with {@code Callable} tasks but not {@code Runnable} tasks</li>
     * </ul>
     * </td>
     * </tr>
     * <tr>
     * <td>{@link ExecutorService#shutdown() Shutdown} executor and wait for all tasks to complete using
     * {@link ExecutorService#awaitTermination(long, TimeUnit) awaitTermination}</td>
     * <td>
     * <ul>
     * <li>Permanently disables the executor, making it unusuable for subsequent execution</li>
     * </ul>
     * </td>
     * </tr>
     * <tr>
     * </tr>
     * </table>
     * <p>
     * In contrast, this method uses a {@link Counter} to track submitted and completed tasks, allowing users to wait for
     * task completion on any set of tasks, submitted through any method, without shutting down the executor or managing
     * individual {@code Future}s.
     * <p>
     * <b>Warning:</b> It is generally required that all tasks intended for the current batch be submitted <i>before</i>
     * calling {@code await} to ensure correct behavior. Race conditions may occur if some threads are actively submitting
     * tasks while other threads are waiting for tasks to complete.
     *
     * @throws InterruptedException if this thread is interrupted while waiting
     */
    public void await() throws InterruptedException {
        svc.await();
    }

    @Override
    public String toString() {
        final String parent = super.toString();
        return this.getClass().getSimpleName() + (parent.substring(0, parent.length() - 1) + ", bound = " + getCorePoolSize() + "]").substring(parent.indexOf('['));
    }

}