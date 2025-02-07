package software.leonov.concurrent;

import static java.util.Objects.requireNonNull;
import static software.leonov.concurrent.PausableThreadPoolExecutor.drainFully;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Static utility methods for working with {@link ThreadPoolExecutor}s and alternative {@link ExecutorService}
 * implementations.
 * 
 * @author Zhenya Leonov
 */
public final class ExecutorServices {

    private ExecutorServices() {
    }

    /**
     * Blocks until all tasks have completed execution after a {@link ExecutorService#shutdown() shutdown} or
     * {@link ExecutorService#shutdownNow() shutdownNow} request, <b>or the current thread is interrupted</b>.
     * <p>
     * Use this method only if you are sure that all tasks in the specified executor will eventually succeed in a reasonable
     * amount of time.
     * 
     * @param exec the specified {@code ExecutorService}
     * @throws InterruptedException if interrupted while waiting
     */
    public static void awaitTermination(final ExecutorService exec) throws InterruptedException {
        requireNonNull(exec, "exec == null");
        exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }

    /**
     * Attempts to {@link ExecutorService#shutdown() shutdown} the specified {@code ExecutorService} and blocks until all
     * tasks have completed execution, <b>or the current thread is interrupted</b>.
     * <p>
     * Use this method only if you are sure that all tasks in the specified executor will eventually succeed in a reasonable
     * amount of time.
     * 
     * @param exec the specified {@code ExecutorService}
     * @throws InterruptedException if interrupted while waiting
     */
    public static void shutdownAndAwaitTermination(final ExecutorService exec) throws InterruptedException {
        requireNonNull(exec, "exec == null");
        exec.shutdown();
        awaitTermination(exec);
    }

    /**
     * Attempts to {@link ExecutorService#shutdownNow() shutdownNow} the specified {@code ExecutorService} and blocks until
     * all tasks have completed execution, <b>or the current thread is interrupted</b>.
     * <p>
     * Use this method only if you are sure that all tasks in the specified executor will eventually succeed in a reasonable
     * amount of time.
     * 
     * @param exec the specified {@code ExecutorService}
     * @return the list of tasks that never commenced execution
     * @throws InterruptedException if interrupted while waiting
     */
    public static List<Runnable> shutdownNowAndAwaitTermination(final ExecutorService exec) throws InterruptedException {
        requireNonNull(exec, "exec == null");
        final List<Runnable> remaining = exec.shutdownNow();
        awaitTermination(exec);
        return remaining;
    }

    /**
     * Attempts to {@link #shutdownFast(ThreadPoolExecutor) shutdownFast} the specified {@code ExecutorService} and blocks
     * until all tasks have completed execution, <b>or the current thread is interrupted</b>.
     * <p>
     * Use this method only if you are sure that all tasks in the specified executor will eventually succeed in a reasonable
     * amount of time.
     * 
     * @param exec the specified {@code ThreadPoolExecutor}
     * @return the list of tasks that never commenced execution
     * @throws InterruptedException if interrupted while waiting
     */
    public static List<Runnable> shutdownFastAndAwaitTermination(final ThreadPoolExecutor exec) throws InterruptedException {
        requireNonNull(exec, "exec == null");
        final List<Runnable> remaining = shutdownFast(exec);
        awaitTermination(exec);
        return remaining;
    }

    /**
     * Halts the processing of pending tasks but does not attempt to stop actively executing tasks. All pending tasks are
     * drained (removed) from the work queue and returned when this method completes.
     * <p>
     * This method does not wait for actively executing tasks to terminate. Use {@link #awaitTermination(ExecutorService)}
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
            synchronized (exec) { // why are we doing this?
                drainFully(queue, tasks);
            }
            return tasks;
        }
    }

}