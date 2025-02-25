package software.leonov.concurrent;

import static java.util.Objects.requireNonNull;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;

/**
 * A {@code BoundedExecutor} enforces a limit on the maximum number of tasks that can be
 * {@link Executor#execute(Runnable) executed} concurrently by another {@code Executor}. Once the maximum number of
 * tasks are executing attempts to execute another task will block until a previous task completes.
 * <p>
 * Users can use this class to control the concurrency in different sections of their code to avoid overloading a shared
 * global thread pool.
 * <p>
 * <b>Note:</b> A {@code BoundedExecutor} does not impose a strict limit on number of threads generated by the
 * underlying executor, as this class has no control over the behavior or implementation of the supplied
 * {@code Executor} class. Restricting task execution does not directly affect the number of threads that the underlying
 * executor may create.
 * <p>
 * Consider an executor with an unrestricted thread pool, that has been optimized to initiate task execution immediately
 * upon arrival. When a new task is executed, a new thread <i>may</i> be created to handle the incoming task, even after
 * a preceding task's completion, for example if the thread executing the previous task is occupied with post-task
 * cleanup or other internal operations.
 *
 * @author Zhenya Leonov
 */
public class BoundedExecutor implements Executor {

    private final Executor exec;
    private final Semaphore semaphore;

    /**
     * Creates a new {@code BoundedExecutor} which will limit the maximum number of tasks that can be executed concurrently
     * by the underlying {@code Executor}.
     * 
     * @param exec   the underlying executor
     * @param ntasks the maximum number of tasks allowed to execute concurrently
     */
    public BoundedExecutor(final Executor exec, final int ntasks) {
        requireNonNull(exec, "exec == null");

        if (ntasks < 1)
            throw new IllegalArgumentException("ntasks < 1");

        this.exec = exec;
        this.semaphore = new Semaphore(ntasks);
    }

    /**
     * {@inheritDoc}
     * <p>
     * If this thread is interrupted while waiting to execute the command this method will return immediately and this
     * thread's {@link Thread#isInterrupted() interrupted} status will be set to {@code true}.
     */
    @Override
    public final void execute(final Runnable command) {
        requireNonNull(command, "command == null");

        try {
            semaphore.acquire();
            exec.execute(() -> {
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
            semaphore.release();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Returns the underlying {@code Executor}.
     * 
     * @return the underlying {@code Executor}
     */
    public Executor getDelegate() {
        return exec;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "[" + super.toString() + "]";
    }

}
