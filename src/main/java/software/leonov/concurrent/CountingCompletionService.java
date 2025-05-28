package software.leonov.concurrent;

import static java.util.Objects.requireNonNull;

import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An {@link ExecutorCompletionService} that maintains an internal count of {@link #getSubmittedCount() submitted} and
 * {@link #getRetrievedCount() retrieved} tasks. Users can call {@link #hasNext()} to determine if more {@link Future}s
 * are available for retrieval, either immediately (from completed tasks) or in the future (from in-progress tasks).
 * <p>
 * This class is well suited to be used in an {@code Iterator} like manner:<pre>{@code 
 *  while(svc.hasNext()) {
 *     Future<R> f = svc.take();
 *     R result = f.get();
 *     ...
 *  }}</pre>
 * <p>
 * <b>Warning:</b> Tasks may be sumbitted concurrently, but it is generally expected that only a single consumer thread
 * retrieve tasks at any one time. Failure to follow this advice may result in unexpected behavior or deadlocks. The
 * values returned from {@link #getRetrievedCount()} and {@link #hasNext()} represent a transient state which may change
 * dynamically if other threads are actively retrieving tasks.
 * <p>
 * For example, if a call to {@link #hasNext()} returns {@code true}, a subsequent call to {@link #take()} is guaranteed
 * to return a {@code Future}, <i>unless</i> another thread retrieves the {@code Future} first between the original call
 * to {@code hasNext()} and the subsequent call to {@code take()}. In that case the caller will block, possibly
 * indefinetly, unless another {@code Future} becomes available.
 * 
 * @param <V> The type of results returned by the tasks
 * @author Zhenya Leonov
 */
public final class CountingCompletionService<V> extends ExecutorCompletionService<V> {

    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong retrieved = new AtomicLong();

    /**
     * Creates a {@code CountingCompletionService} using the specified executor for base task execution and a
     * {@link LinkedBlockingQueue} as a completion queue.
     *
     * @param executor the executor to use
     */
    public CountingCompletionService(final Executor executor) {
        super(executor);
    }

    /**
     * Creates a {@code CountingCompletionService} using the specified executor for base task execution and the supplied
     * {@code queue} as its completion queue.
     * <p>
     * <b>Warning:</b> The supplied {@code queue} is assumed to be unbounded. If an attempt to {@link Queue#add(Object) add}
     * a task fails it will be unretrievable.
     *
     * @param executor the executor to use
     * @param queue    the queue to use as the completion queue
     */
    public CountingCompletionService(final Executor executor, final BlockingQueue<Future<V>> queue) {
        super(executor, queue);
    }

    @Override
    public Future<V> submit(final Callable<V> task) {
        requireNonNull(task, "task == null");
        final Future<V> future = super.submit(task);
        submitted.incrementAndGet();
        return future;
    }

    @Override
    public Future<V> submit(final Runnable task, final V result) {
        requireNonNull(task, "task == null");
        final Future<V> future = super.submit(task, result);
        submitted.incrementAndGet();
        return future;
    }

    @Override
    public Future<V> take() throws InterruptedException {
        final Future<V> future = super.take();
        retrieved.incrementAndGet();
        return future;
    }

    @Override
    public Future<V> poll() {
        final Future<V> future = super.poll();
        if (future != null)
            retrieved.incrementAndGet();
        return future;
    }

    @Override
    public Future<V> poll(final long timeout, final TimeUnit unit) throws InterruptedException {
        final Future<V> future = super.poll(timeout, unit);
        if (future != null)
            retrieved.incrementAndGet();
        return future;
    }

    /**
     * Returns {@code true} if more {@link Future}s are available for retrieval, either immediately (from completed tasks)
     * or in the future (from in-progress tasks).
     *
     * @return {@code true} if more {@link Future}s are available for retrieval
     */
    public boolean hasNext() {
        return retrieved.get() < submitted.get();
    }

    /**
     * Returns the number of tasks which have been retrieved via the {@link #take()}, {@link #poll()}, or
     * {@link #poll(long, TimeUnit)} methods.
     * 
     * @return the number of retrieved tasks
     */
    public long getRetrievedCount() {
        return retrieved.get();
    }

    /**
     * Returns the number of tasks that have been submitted via the {@link #submit(Callable)} or
     * {@link #submit(Runnable, Object)} methods.
     * 
     * @return the number of submitted tasks
     */
    public long getSubmittedCount() {
        return submitted.get();
    }

}
