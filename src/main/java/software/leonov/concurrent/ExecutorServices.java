package software.leonov.concurrent;

import static java.util.Objects.requireNonNull;
import static software.leonov.concurrent.PausableThreadPoolExecutor.drainFully;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Static utility methods for {@link ExecutorService}s.
 * 
 * @author Zhenya Leonov
 */
public final class ExecutorServices {

    private ExecutorServices() {
    }

    /**
     * Creates a thread pool that reuses a fixed number of threads. At any point, at most {@code nthreads} threads will be
     * active processing tasks. If additional tasks are submitted when all threads are active, they will wait in the queue
     * until a thread is available. If any thread terminates due to a failure during execution prior to shutdown, a new one
     * will take its place if needed to execute subsequent tasks. The threads in the pool will exist until it is explicitly
     * {@link ExecutorService#shutdown shutdown}.
     *
     * @param nthreads the number of threads in the pool
     * @param queue    the queue to use for holding tasks before they are executed
     * @return the newly created thread pool
     * @throws IllegalArgumentException if {@code nthreads < 1}
     */
    public static ExecutorService newFixedThreadPool(final int nthreads, final BlockingQueue<Runnable> queue) {
        return newFixedThreadPool(nthreads, queue, Executors.defaultThreadFactory());
    }

    /**
     * Creates a thread pool that reuses a fixed number of threads. At any point, at most {@code nthreads} threads will be
     * active processing tasks. If additional tasks are submitted when all threads are active, they will wait in the queue
     * until a thread is available. If any thread terminates due to a failure during execution prior to shutdown, a new one
     * will take its place if needed to execute subsequent tasks. The threads in the pool will exist until it is explicitly
     * {@link ExecutorService#shutdown shutdown}.
     *
     * @param nthreads the number of threads in the pool
     * @param queue    the queue to use for holding tasks before they are executed
     * @param factory  the factory to use when the executor creates a new thread
     * @return the newly created thread pool
     * @throws IllegalArgumentException if {@code nthreads < 1}
     */
    public static ExecutorService newFixedThreadPool(final int nthreads, final BlockingQueue<Runnable> queue, final ThreadFactory factory) {
        requireNonNull(queue, "queue == null");
        requireNonNull(factory, "factory == null");

        if (nthreads < 1)
            throw new IllegalArgumentException("nthreads < 1");

        return new ThreadPoolExecutor(nthreads, nthreads, 0L, TimeUnit.MILLISECONDS, queue, factory);
    }

    /**
     * Waits for the specified {@code ExecutorService} to terminate after a {@link ExecutorService#shutdown() shutdown()} or
     * {@link ExecutorService#shutdownNow() shutdownNow()} request.
     * <p>
     * <b>Warning:</b> This method will block forever until the {@code ExecutorService} terminates. If active tasks are
     * deadlocked this thread must be interrupted.
     * 
     * @param exec the specified {@code ExecutorService}
     * @throws InterruptedException if interrupted while waiting
     */
    public static void ensureTermination(final ExecutorService exec) throws InterruptedException {
        requireNonNull(exec, "exec == null");
        exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }

    /**
     * Attempts to {@link ExecutorService#shutdown() shutdown} the specified {@code ExecutorService} and
     * {@link #ensureTermination(ExecutorService) waits} for it to terminate.
     * <p>
     * <b>Warning:</b> This method will block forever until the {@code ExecutorService} terminates. If active tasks are
     * deadlocked this thread must be interrupted.
     * 
     * @param exec the specified {@code ExecutorService}
     * @throws InterruptedException if interrupted while waiting
     */
    public static void shutdownAndEnsureTermination(final ExecutorService exec) throws InterruptedException {
        requireNonNull(exec, "exec == null");
        exec.shutdown();
        ensureTermination(exec);
    }

    /**
     * Attempts to {@link ExecutorService#shutdownNow() shutdownNow} the specified {@code ExecutorService} and
     * {@link #ensureTermination(ExecutorService) waits} for it to terminate.
     * <p>
     * <b>Warning:</b>This method will block forever until the {@code ExecutorService} terminates. If active tasks are
     * deadlocked this thread must be interrupted.
     * 
     * @param exec the specified {@code ExecutorService}
     * @throws InterruptedException if interrupted while waiting
     */
    public static void shutdownNowAndEnsureTermination(final ExecutorService exec) throws InterruptedException {
        requireNonNull(exec, "exec == null");
        exec.shutdownNow();
        ensureTermination(exec);
    }

    /**
     * Attempts to {@link #shutdownFast(ThreadPoolExecutor)} the specified {@code ThreadPoolExecutor} and
     * {@link #ensureTermination(ExecutorService) waits} for it to terminate.
     * <p>
     * <b>Warning:</b> This method will block forever until the {@code ThreadPoolExecutor} terminates. If active tasks are
     * deadlocked this thread must be interrupted.
     * 
     * @param exec the specified {@code ThreadPoolExecutor}
     * @throws InterruptedException if interrupted while waiting
     */
    public static void shutdownFastAndEnsureTermination(final ThreadPoolExecutor exec) throws InterruptedException {
        requireNonNull(exec, "exec == null");
        shutdownFast(exec);
        ensureTermination(exec);
    }

    /**
     * Halts the processing of pending tasks but does not attempt to stop actively executing tasks. All pending tasks are
     * drained (removed) from the work queue and returned when this method completes.
     * <p>
     * This method does not wait for actively executing tasks to terminate. Use {@link #ensureTermination(ExecutorService)}
     * or {@link ThreadPoolExecutor#awaitTermination(long, TimeUnit)} to do that.
     * <p>
     * This method is the middle ground between {@link ExecutorService#shutdown()} and
     * {@link ExecutorService#shutdownNow()}:
     * <ul>
     * <li>{@link ExecutorService#shutdown() shutdown()}: all actively executing tasks and pending tasks are allowed to
     * continue, but no new tasks will be accepted</li>
     * <li><b>shutdownFast()</b>: all actively executing tasks are allowed to continue, <b>pending tasks are removed</b>,
     * and no new tasks will be accepted</li>
     * <li>{@link ExecutorService#shutdownNow() shutdownNow()}: all actively executing tasks are <u>interrupted</u>, pending
     * tasks are removed, and no new tasks will be accepted</li>
     * </ul>
     * 
     * @param exec the specified {@code ThreadPoolExecutor}
     * @return the list of pending tasks
     */
    public static List<Runnable> shutdownFast(final ThreadPoolExecutor exec) {
        requireNonNull(exec, "exec == null");

        if (exec instanceof PausableThreadPoolExecutor)
            return ((PausableThreadPoolExecutor) exec).shutdownFast();
        else {
            exec.shutdown();
            final BlockingQueue<Runnable> queue = exec.getQueue();
            final List<Runnable> tasks = new ArrayList<>(queue.size());
            synchronized (exec) {
                drainFully(queue, tasks);
            }
            return tasks;
        }
    }

}