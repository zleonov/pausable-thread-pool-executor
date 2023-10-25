package software.leonov.concurrent;

import static java.util.Objects.requireNonNull;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RunnableFuture;

public final class ExecutorServiceQueueLimiter<T> {

    public static enum RejectedExecutionPolicy {
        ABORT, BLOCK, RUN;
    }

    private final ExecutorService exec;
    private final BlockingQueue<Runnable> queue;
    private final RejectedExecutionPolicy policy;

    public ExecutorServiceQueueLimiter(final ExecutorService exec, final int limit, final RejectedExecutionPolicy policy) {
        requireNonNull(exec, "exec == null");
        requireNonNull(exec, "policy == policy");

        if (limit < 1)
            throw new IllegalArgumentException("limit < 1");

        this.exec = exec;
        this.queue = new ArrayBlockingQueue<>(limit);
        this.policy = policy;
    }

    public Future<T> submit(final Callable<T> task) throws InterruptedException {
        requireNonNull(exec, "task == null");

        final RunnableFuture<T> f = new FutureTask<T>(task);

        if (!queue.offer(f)) {
            switch (policy) {
            case ABORT:
                throw new RejectedExecutionException();
            case BLOCK:
                queue.put(f);
            case RUN:
                f.run();
            default:
                break;
            }
        } else
            try {
                exec.execute(new FutureImpl(f));
            } catch (final RejectedExecutionException e) {
                queue.remove(f);
                throw e;
            }

        return f;
    }

    public Future<T> submit(final Runnable task, final T result) throws InterruptedException {
        requireNonNull(exec, "task == null");
        return submit(Executors.callable(task, result));
    }

    public Future<T> submit(final Runnable task) throws InterruptedException {
        return submit(task, null);
    }

    public void execute(final Runnable task) throws InterruptedException {
        submit(task);
    }

    private class FutureImpl extends FutureTask<T> {
        private FutureImpl(final RunnableFuture<T> task) {
            super(task, null);
        }

        protected void done() {
            queue.remove(this);
        }
    }

}
