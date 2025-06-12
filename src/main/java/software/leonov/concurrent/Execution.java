package software.leonov.concurrent;

import static java.util.Objects.requireNonNull;
import static software.leonov.concurrent.PausableThreadPoolExecutor.drainFully;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * Static utility methods for working with {@link ThreadPoolExecutor}s and other {@link ExecutorService}s.
 * 
 * @author Zhenya Leonov
 */
public final class Execution {

    private Execution() {
    }

    /**
     * Blocks until all tasks have completed execution after a {@link ExecutorService#shutdown() shutdown} or
     * {@link ExecutorService#shutdownNow() shutdownNow} request, <b>or the current thread is interrupted</b>.
     * <p>
     * Use this method only if you can guarantee that all tasks in the specified executor will eventually succeed in a
     * reasonable amount of time.
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
     * Use this method only if you can guarantee that all tasks in the specified executor will eventually succeed in a
     * reasonable amount of time.
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
     * Use this method only if you can guarantee that all tasks in the specified executor will eventually succeed in a
     * reasonable amount of time.
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
     * Use this method only if you can guarantee that all tasks in the specified executor will eventually succeed in a
     * reasonable amount of time.
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
     * drained (removed) from the task queue and returned when this method completes.
     * <p>
     * This method does not wait for actively executing tasks to terminate. Use {@link #awaitTermination(ExecutorService)}
     * or {@link ThreadPoolExecutor#awaitTermination(long, TimeUnit)} to do that.
     * <p>
     * This method is the middle ground between {@link ExecutorService#shutdown()} and
     * {@link ExecutorService#shutdownNow()}:
     * <ul>
     * <li>{@link ExecutorService#shutdown() shutdown()}: all actively executing tasks and pending tasks are allowed to
     * continue, but no new tasks will be accepted</li>
     * <li><b>shutdownFast():</b> all actively executing tasks are allowed to continue, <b>pending tasks are removed</b>,
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
            final List<Runnable>          tasks = new ArrayList<>(queue.size());
            synchronized (exec) { // why are we doing this?
                drainFully(queue, tasks);
            }
            return tasks;
        }
    }

    /**
     * Executes the given tasks, returning a list of {@code Future}s holding their status when all tasks complete.
     * {@link Future#isDone} is {@code true} for each element of the returned list. Note that a task could have
     * <i>completed</i> normally or by throwing an exception. The {@link Future#get() get()} method of each {@code Future}
     * will return {@code null} upon <i>successful</i> completion.
     * <p>
     * This method is a shorthand that {@link Executors#callable(Runnable) adapts} {@code Runnable} tasks to
     * {@code Callable} tasks before calling {@link ExecutorService#invokeAll(Collection)}.
     * 
     * @param exec  the executor which will execute the tasks
     * @param tasks the collection of tasks
     * @return a list of Futures representing the tasks given task list, each of which has completed
     * @throws InterruptedException       if interrupted while waiting
     * @throws RejectedExecutionException if any task cannot be scheduled for execution
     */
    public static List<Future<Object>> invokeAll(final ExecutorService exec, final Collection<? extends Runnable> tasks) throws InterruptedException, RejectedExecutionException {
        requireNonNull(exec, "exec == null");
        requireNonNull(tasks, "tasks == null");
        return exec.invokeAll(new TransformedCollection<>(tasks, Executors::callable));
    }

    /**
     * Executes the given tasks, returning a list of {@code Future}s holding their status when all tasks complete or the
     * {@code timeout} expires, whichever happens first. {@link Future#isDone} is {@code true} for each element of the
     * returned list. Tasks which have not completed are {@link Future#cancel(boolean) cancelled}. Note that a task could
     * have <i>completed</i> normally or by throwing an exception. The {@link Future#get() get()} method of each
     * {@code Future} will return {@code null} upon <i>successful</i> completion.
     * <p>
     * This method is a shorthand that {@link Executors#callable(Runnable) adapts} {@code Runnable} tasks to
     * {@code Callable} tasks before calling {@link ExecutorService#invokeAll(Collection, long, TimeUnit)}.
     * 
     * @param exec    the executor which will execute the tasks
     * @param tasks   the collection of tasks
     * @param timeout the maximum time to wait
     * @param unit    the time unit of the timeout argument
     * @return a list of {@code Future}s representing the completed tasks (if the {@code timeout} expires some of these
     *         tasks will not have completed)
     * @throws InterruptedException       if interrupted while waiting
     * @throws RejectedExecutionException if any task cannot be scheduled for execution
     */
    public static List<Future<Object>> invokeAll(final ExecutorService exec, final Collection<? extends Runnable> tasks, final long timeout, final TimeUnit unit) throws InterruptedException, RejectedExecutionException {
        requireNonNull(exec, "exec == null");
        requireNonNull(tasks, "tasks == null");
        requireNonNull(tasks, "unit == null");
        return exec.invokeAll(new TransformedCollection<>(tasks, Executors::callable), timeout, unit);
    }

    /**
     * Executes the given tasks, returning the result of the first one that has completed successfully, before the given
     * timeout elapses. Upon normal or exceptional return, tasks that have not completed are cancelled. The results of this
     * method are undefined if the given collection is modified while this operation is in progress.
     * <p>
     * This method is a shorthand that {@link Executors#callable(Runnable) adapts} {@code Runnable} tasks to
     * {@code Callable} tasks before calling {@link ExecutorService#invokeAny(Collection)}.
     * 
     * @param exec    the executor which will execute the tasks
     * @param tasks   the collection of tasks
     * @param timeout the maximum time to wait
     * @param unit    the time unit of the timeout argument
     * @return the result returned by one of the tasks
     * @throws InterruptedException       if interrupted while waiting
     * @throws ExecutionException         if no task successfully completes
     * @throws RejectedExecutionException if tasks cannot be scheduled for execution
     */
    public static Object invokeAny(final ExecutorService exec, final Collection<? extends Runnable> tasks) throws InterruptedException, ExecutionException, RejectedExecutionException {
        requireNonNull(exec, "exec == null");
        requireNonNull(tasks, "tasks == null");
        requireNonNull(tasks, "unit == null");
        return exec.invokeAny(new TransformedCollection<>(tasks, Executors::callable));
    }

    /**
     * Executes the given tasks, returning the result of the first one that has completed successfully, before the given
     * timeout elapses. Upon normal or exceptional return, tasks that have not completed are cancelled. The results of this
     * method are undefined if the given collection is modified while this operation is in progress.
     * <p>
     * This method is a shorthand that {@link Executors#callable(Runnable) adapts} {@code Runnable} tasks to
     * {@code Callable} tasks before calling {@link ExecutorService#invokeAny(Collection, long, TimeUnit)}.
     *
     * @param exec    the executor which will execute the tasks
     * @param tasks   the collection of tasks
     * @param timeout the maximum time to wait
     * @param unit    the time unit of the timeout argument
     * @return the result returned by one of the tasks
     * @throws InterruptedException       if interrupted while waiting
     * @throws ExecutionException         if no task successfully completes
     * @throws TimeoutException           if the given timeout elapses before any task successfully completes
     * @throws RejectedExecutionException if tasks cannot be scheduled for execution
     */
    public static Object invokeAny(final ExecutorService exec, final Collection<? extends Runnable> tasks, final long timeout, final TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException, RejectedExecutionException {
        requireNonNull(exec, "exec == null");
        requireNonNull(tasks, "tasks == null");
        requireNonNull(tasks, "unit == null");
        return exec.invokeAny(new TransformedCollection<>(tasks, Executors::callable), timeout, unit);
    }

    /**
     * Add a shutdown hook to block until all tasks in the specified {@code ExecutorService} have completed execution.
     * <p>
     * Use this method only if you can guarantee that all tasks in the specified executor will eventually succeed in a
     * reasonable amount of time.
     *
     * @param exec the specified {@code ExecutorService}
     */
    public static void shutdownAndAwaitTerminationOnSystemExit(final ExecutorService exec) {
        shutdownAndAwaitTerminationOnSystemExit(exec, Duration.of(Long.MAX_VALUE, ChronoUnit.NANOS));

    }

    /**
     * Add a shutdown hook to wait until all tasks in the specified {@code ExecutorService} have completed execution.
     *
     * @param exec    the specified {@code ExecutorService}
     * @param timeout the maximum time to wait
     * @throws ArithmeticException if {@code duration} is too large to fit in a {@code long} nanoseconds value
     */
    public static void shutdownAndAwaitTerminationOnSystemExit(final ExecutorService exec, final Duration timeout) {
        requireNonNull(exec, "exec == null");
        requireNonNull(timeout, "timeout == null");
        final Thread t = new Thread(() -> {
            try {
                exec.awaitTermination(timeout.toNanos(), TimeUnit.NANOSECONDS);
            } catch (final InterruptedException e) {
            }
        });
        t.setName("ShutdownHook-for-" + exec.getClass().getSimpleName());

        Runtime.getRuntime().addShutdownHook(t);
    }

    private static class TransformedCollection<F, T> extends AbstractCollection<T> {
        final Collection<F>                    from;
        final Function<? super F, ? extends T> function;

        private TransformedCollection(final Collection<F> from, final Function<? super F, ? extends T> function) {
            this.from     = from;
            this.function = function;
        }

        @Override
        public Iterator<T> iterator() {
            final Iterator<F> itor = from.iterator();
            return new Iterator<T>() {

                @Override
                public boolean hasNext() {
                    return itor.hasNext();
                }

                @Override
                public T next() {
                    return function.apply(itor.next());
                }
            };
        }

        @Override
        public int size() {
            return from.size();
        }
    }

}