package software.leonov.concurrent;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.Executors.defaultThreadFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * An implementation of {@code PausableExecutorService} based on a {@code ThreadPoolExecutor} with additional
 * convenience methods. Instances of this class can be created via the {@link #newFixedThreadPool()} and
 * {@link #newCachedThreadPool()} builders. After creation this executor is not re-configurable. The
 * {@link #setCorePoolSize(int)} and {@link #setMaximumPoolSize(int)} methods are disabled.
 * <p>
 * <b>Pause/resume functionality</b><br>
 * This executor builds on the <i>extension example</i> provided in the documentation of {@link ThreadPoolExecutor}. The
 * {@link #pause()} and {@link #resume()} methods allow users to halt or resume processing of pending tasks (pausing the
 * executor does not affect actively executing tasks).
 * <p>
 * The executor can be paused at any point unless it is {@link #isShutdown() shutting down} and the {@link #getQueue()
 * work queue} is empty. Note that calling {@link #shutdown()} does not automatically resume the executor. To do that
 * call {@link #shutdownFast()} or {@link #shutdownNow()}.
 * <p>
 * Be careful of race conditions if the pause/resume functionality is used to control program flow. The boolean value
 * returned from calling {@code pause()} and {@link #isPaused()} reflects a transient state which may already be invalid
 * when the call returns if other threads are modifying the state of the executor: for example if another thread calls
 * {@code shutdownNow()}.
 * <p>
 * <b>Callback functions</b><br>
 * The {@link #beforePause(Consumer)}, {@link #afterPause(Consumer)}, {@link #beforeExecute(Consumer)},
 * {@link #afterExecute(BiConsumer)}, and {@link #afterTerminated(Runnable)} methods allow users to register callback
 * functions which will be run when executor is paused/resumed, before/after task execution, and when the executor has
 * terminated, (in that order) respectively. Most commonly used for logging, statistics gathering, or to initialize
 * {@code ThreadLocal} variables.
 * <p>
 * This is a convenient alternative to the idiomatic pre Java 8 style of extending classes for the purpose of overriding
 * empty <i>hook</i> methods.
 * <p>
 * <b>CallerBlocksPolicy for rejected tasks</b><br>
 * The {@code ThreadPoolExecutor} implementation provides 4 <i>saturation policies</i> to handle rejected tasks when a
 * bounded {@link ThreadPoolExecutor#getQueue() work queue} fills up:
 * {@link java.util.concurrent.ThreadPoolExecutor.AbortPolicy AbortPolicy} (default),
 * {@link java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy CallerRunsPolicy},
 * {@link java.util.concurrent.ThreadPoolExecutor.DiscardPolicy DiscardPolicy}, and
 * {@link java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy DiscardOldestPolicy}. There is no predefined
 * saturation policy to make the {@code execute} method block when the work queue is full.
 * <p>
 * {@code PausableThreadPoolExecutor} provides a {@link PausableThreadPoolExecutor.CallerBlocksPolicy
 * CallerBlocksPolicy} which blocks the calling thread until there is an open slot in the
 * {@link ThreadPoolExecutor#getQueue() work queue} and submits the task again. Typically this will be used with a
 * bounded {@link ArrayBlockingQueue}.
 * <p>
 * <b>Convenience methods</b><br>
 * The {@link #shutdownFast()} method is the middle ground between {@link #shutdown() shutdown()} and
 * {@link #shutdownNow()} and the {@link #awaitTermination()} method is a shorthand for
 * {@link #awaitTermination(long, TimeUnit) awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS)}.
 * 
 * @author Zhenya Leonov
 */
public final class PausableThreadPoolExecutor extends ThreadPoolExecutor implements PausableExecutorService {

    private static final Consumer<Runnable> DO_NOTHING_CONSUMER = r -> {
    };

    private static final Runnable DO_NOTHING_RUNNABLE = () -> {
    };

    private static final BiConsumer<Runnable, Throwable> DO_NOTHING_BI_CONSUMER = (r, t) -> {
    };

    private final ReentrantLock lock      = new ReentrantLock();
    private final Condition     condition = lock.newCondition();
    private boolean             paused    = false;

    private Consumer<Runnable>              beforeExecute   = DO_NOTHING_CONSUMER;
    private BiConsumer<Runnable, Throwable> afterExecute    = DO_NOTHING_BI_CONSUMER;
    private Consumer<Runnable>              beforePause     = DO_NOTHING_CONSUMER;
    private Consumer<Runnable>              afterPause      = DO_NOTHING_CONSUMER;
    private Runnable                        afterTerminated = DO_NOTHING_RUNNABLE;

    private PausableThreadPoolExecutor(final int corePoolSize, final int maxPoolSize, final long keepAliveTime, final TimeUnit unit, final BlockingQueue<Runnable> queue, final ThreadFactory factory, final RejectedExecutionHandler handler) {
        super(corePoolSize, maxPoolSize, keepAliveTime, unit, queue, factory, handler);
    }

    @Override
    protected void beforeExecute(final Thread t, final Runnable r) {
        super.beforeExecute(t, r);
        lock.lock();
        try {
            if (paused) {
                beforePause.accept(r);
                while (paused)
                    condition.await();
                afterPause.accept(r);
            }
        } catch (final InterruptedException e) {
            t.interrupt();
        } finally {
            lock.unlock();
        }
        beforeExecute.accept(r);
    }

    @Override
    protected void afterExecute(final Runnable r, final Throwable t) {
        super.afterExecute(r, t);
        lock.lock();
        try {
            if (isShutdown() && getQueue().isEmpty())
                paused = false;
        } finally {
            lock.unlock();
        }
        afterExecute.accept(r, t);
    }

    @Override
    public boolean isPaused() {
        lock.lock();
        try {
            return paused;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean pause() {
        lock.lock();
        try {
            paused = !isShutdown();
            return paused;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void resume() {
        lock.lock();
        try {
            if (isPaused()) {
                paused = false;
                condition.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<Runnable> shutdownFast() {
        lock.lock();
        try {
            super.shutdown();
            final List<Runnable> tasks = new ArrayList<>(getQueue().size());
            drainFully(getQueue(), tasks);
            resume();
            return tasks;
        } finally {
            lock.unlock();
        }

    }

    @Override
    public List<Runnable> shutdownNow() {
        lock.lock();
        try {
            final List<Runnable> tasks = super.shutdownNow();
            resume();
            return tasks;
        } finally {
            lock.unlock();
        }
    }

    @Override
    protected void terminated() {
        super.terminated();
        afterTerminated.run();
    }

    /**
     * Registers a callback function which will be invoked when this {@code PausableThreadPoolExecutor} has
     * {@link #terminated}.
     * 
     * @param callback the callback function
     * @return this {@code PausableThreadPoolExecutor} instance
     */
    public PausableThreadPoolExecutor afterTerminated(final Runnable callback) {
        requireNonNull(callback, "callback == null");
        this.afterTerminated = callback;
        return this;

    }

    /**
     * Registers a callback function which will be invoked prior to executing the task in the given {@code Thread}. This
     * callback is invoked by the thread that executed the task and may be used to reinitialize {@code ThreadLocal}s or to
     * perform logging.
     * <p>
     * Provides equivalent functionality as overriding of {@link ThreadPoolExecutor#beforeExecute(Thread, Runnable)} in
     * extending classes.
     * 
     * @param callback the callback function
     * @return this {@code PausableThreadPoolExecutor} instance
     */
    public PausableThreadPoolExecutor beforeExecute(final Consumer<Runnable> callback) {
        requireNonNull(callback, "callback == null");
        this.beforeExecute = callback;
        return this;
    }

    /**
     * Registers a callback function which will be invoked upon completion the task. This callback is invoked by the thread
     * that executed the task. If non-null, the {@code Throwable} is the uncaught {@code RuntimeException} or {@code Error}
     * that caused execution to terminate abruptly.
     * <p>
     * Provides equivalent functionality as overriding of {@link ThreadPoolExecutor#afterExecute(Runnable, Throwable)} in
     * extending classes. See the {@code afterExecute} documentation for notes on differentiating {@code FutureTask}s from
     * generic {@code Runnable}s.
     * 
     * @param callback the callback function
     * @return this {@code PausableThreadPoolExecutor} instance
     */
    public PausableThreadPoolExecutor afterExecute(final BiConsumer<Runnable, Throwable> callback) {
        requireNonNull(callback, "callback == null");
        this.afterExecute = callback;
        return this;
    }

    /**
     * Registers a callback function which will be invoked immediately before a thread in the pool has {@link #pause()
     * paused}. This callback is invoked by the thread that will be executing the task.
     * 
     * @param callback the callback function
     * @return this {@code PausableThreadPoolExecutor} instance
     */
    public PausableThreadPoolExecutor beforePause(final Consumer<Runnable> callback) {
        requireNonNull(callback, "callback == null");
        this.beforePause = callback;
        return this;
    }

    /**
     * Registers a callback function which will be invoked immediately after a thread in the pool has {@link #resume()
     * resumed}. This callback is invoked by the thread that will be executing the task.
     * 
     * @param callback the callback function
     * @return this {@code PausableThreadPoolExecutor} instance
     */
    public PausableThreadPoolExecutor afterPause(final Consumer<Runnable> callback) {
        requireNonNull(callback, "callback == null");
        this.afterPause = callback;
        return this;
    }

    /**
     * A handler for rejected tasks that blocks the calling thread until there is an open slot in the
     * {@link ThreadPoolExecutor#getQueue() work queue} and submits the task again.
     * <p>
     * This policy will have the same behavior as {@link java.util.concurrent.ThreadPoolExecutor.AbortPolicy AbortPolicy} if
     * the queue is unbounded.
     * 
     * @author Zhenya Leonov
     */
    public static class CallerBlocksPolicy implements RejectedExecutionHandler {

        private final Runnable before;
        private final Runnable after;

        /**
         * Creates a new {@code CallerBlocksPolicy}.
         */
        public CallerBlocksPolicy() {
            this(DO_NOTHING_RUNNABLE, DO_NOTHING_RUNNABLE);
        }

        /**
         * Creates a new {@code CallerBlocksPolicy}.
         * 
         * @param before the runnable to run immediately before waiting for space to become available in the work queue (usually
         *               used for debugging and logging)
         * @param after  the runnable to run immediately after the task has been inserted into the work queue (usually used for
         *               debugging and logging)
         */
        public CallerBlocksPolicy(final Runnable before, final Runnable after) {
            requireNonNull(before, "before == null");
            requireNonNull(after, "after == null");
            this.before = before;
            this.after  = after;

        }

        @Override
        public void rejectedExecution(final Runnable r, final ThreadPoolExecutor executor) {
            if (executor.isShutdown())
                throw new RejectedExecutionException();

            try {
                before.run();
                executor.getQueue().put(r);
                after.run();
            } catch (final InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RejectedExecutionException(ie);
            }
        }
    }

    /**
     * This operation is not supported.
     * 
     * @throws UnsupportedOperationException always
     */
    @Override
    public void allowCoreThreadTimeOut(boolean value) {
        throw new UnsupportedOperationException();
    }

    private PausableThreadPoolExecutor setAllowCoreThreadTimeOut(final boolean allowCoreThreadTimeOut) {
        super.allowCoreThreadTimeOut(allowCoreThreadTimeOut);
        return this;
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
    public void setKeepAliveTime(long time, TimeUnit unit) {
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
    public void setRejectedExecutionHandler(RejectedExecutionHandler handler) {
        throw new UnsupportedOperationException();
    }

    /**
     * This operation is not supported.
     * 
     * @throws UnsupportedOperationException always
     */
    public void setThreadFactory(ThreadFactory threadFactory) {
        throw new UnsupportedOperationException();
    }

    /**
     * Attempts to remove all elements from the specified queue and adds them to the given collection, first by calling
     * {@link BlockingQueue#drainTo(Collection) drainTo(Collection)}, then, if the queue is still not empty because it is a
     * {@link DelayQueue} or another kind of queue for which {@link Queue#poll() poll()} or
     * {@link BlockingQueue#drainTo(Collection) drainTo(Collection)} may fail to remove some elements, this method iterates
     * through {@link Collection#toArray() queue.toArray()} and transfers the remaining elements one by one.
     * 
     * @param queue the specified queue
     * @param tasks the collection to transfer elements into
     * @return the number of elements transferred
     */
    @SuppressWarnings("unchecked")
    static <T> int drainFully(final BlockingQueue<? extends T> queue, final Collection<? super T> tasks) {
        requireNonNull(queue, "queue == null");
        requireNonNull(tasks, "tasks == null");

        int count = queue.drainTo(tasks);
        if (!queue.isEmpty())
            for (final T r : (T[]) queue.toArray())
                if (queue.remove(r)) {
                    tasks.add(r);
                    count++;
                }
        return count;
    }

//    @Override
//    public String toString() {
//        return super.toString() + " (" + (isPaused() ? "paused)" : "not paused)");
//    }

    @Override
    public String toString() {
        return isPaused() ? super.toString() + " (paused)" : super.toString();
    }

    /**
     * Returns a builder which creates {@code PausableThreadPoolExecutor} instances that use a fixed number of threads is
     * equal to the {@link Runtime#availableProcessors() availableProcessors}.
     * 
     * @return a builder which creates {@code PausableThreadPoolExecutor}s instances that use a fixed number of threads is
     *         equal to the {@link Runtime#availableProcessors() availableProcessors}
     */
    public static FixedThreadPoolBuilder newFixedThreadPool() {
        return new FixedThreadPoolBuilder(Runtime.getRuntime().availableProcessors());
    }

    /**
     * Returns a builder which creates {@code PausableThreadPoolExecutor} instances that use a fixed number of threads.
     * 
     * @param nthreads the size of the pool
     * @return a builder which creates {@code PausableThreadPoolExecutor}s instances that use a fixed number of threads
     */
    public static FixedThreadPoolBuilder newFixedThreadPool(final int nthreads) {
        if (nthreads < 1)
            throw new IllegalArgumentException("nthreads < 1");
        return new FixedThreadPoolBuilder(nthreads);
    }

    /**
     * Returns a builder which creates {@code PausableThreadPoolExecutor} instances that use a single worker thread.
     * 
     * @return a builder which creates {@code PausableThreadPoolExecutor}s instances that use a single worker thread
     */
    public static FixedThreadPoolBuilder newSingleThreadPool() {
        return new FixedThreadPoolBuilder(1);
    }

    /**
     * Returns a builder which creates {@code PausableThreadPoolExecutor} instances which create new threads as needed.
     * <p>
     * <b>Warning: It is <u>strongly discouraged</u> to create a cached thread pool with the intention of using
     * {@link PausableThreadPoolExecutor#pause() pause}/{@link PausableThreadPoolExecutor#resume() resume} functionality,
     * unless you have strict control over the maximum concurrency level outside the pool!</b> A thread in the pool only
     * becomes aware of a pause request in the {@link ThreadPoolExecutor#beforeExecute(Thread, Runnable)} method, after it
     * is created and assigned to execute a task from the {@link ThreadPoolExecutor#getQueue() work queue}. In the presence
     * of unlimited incoming tasks, a cached thread pool in a {@link PausableThreadPoolExecutor#isPaused() paused} state
     * will continue to create new threads indefinitely as each thread is assigned a task and then paused before execution.
     * <p>
     * <b>It is only recommended to use a cached thread pool with a {@link BoundedExecutor} or
     * {@link BoundedExecutorService}.</b>
     * 
     * @return a builder which creates {@code PausableThreadPoolExecutor}s instances which create new threads as needed
     */
    public static CachedThreadPoolBuilder newCachedThreadPool() {
        return new CachedThreadPoolBuilder();
    }

    /**
     * A builder which creates {@code PausableThreadPoolExecutor} instances that use a fixed number of threads. The default
     * number of threads is equal to the {@link Runtime#availableProcessors() availableProcessors}.
     * <p>
     * If the {@link #setWorkQueue(BlockingQueue) work queue}, {@link #setThreadFactory(ThreadFactory) thread factory},
     * {@link #setKeepAliveTime(Duration) keep-alive time}, and
     * {@link #setRejectedExecutionHandler(RejectedExecutionHandler) rejected execution handler} are left unspecified a
     * {@link LinkedBlockingQueue}, {@link Executors#defaultThreadFactory() default thread factory}, {@link Duration#ZERO no
     * keep-alive time}, and {@link java.util.concurrent.ThreadPoolExecutor.AbortPolicy AbortPolicy} will be used
     * respectively.
     */
    public static class FixedThreadPoolBuilder {

        private final int poolSize;

        protected BlockingQueue<Runnable>  queue         = null;
        protected ThreadFactory            factory       = null;
        protected RejectedExecutionHandler handler       = null;
        protected long                     keepAliveTime = 0;

        // Constructor for CachedThreadPoolBuilder
        FixedThreadPoolBuilder() {
            this.poolSize = 0;
        }

        FixedThreadPoolBuilder(final int poolSize) {
            this.poolSize = poolSize;
        }

        /**
         * Sets the queue to use for holding tasks before they are executed.
         * 
         * @param queue the queue to use for holding tasks before they are executed
         * @return {@code this} builder instance
         */
        public FixedThreadPoolBuilder setWorkQueue(final BlockingQueue<Runnable> queue) {
            requireNonNull(queue, "queue == null");
            this.queue = queue;
            return this;
        }

        /**
         * Sets the factory to use when the executor creates a new thread.
         * 
         * @param factory the factory to use when the executor creates a new thread
         * @return {@code this} builder instance
         */
        public FixedThreadPoolBuilder setThreadFactory(final ThreadFactory factory) {
            requireNonNull(factory, "factory == null");
            this.factory = factory;
            return this;
        }

        /**
         * Sets the handler to use when execution is blocked because the thread bounds and queue capacities are reached.
         * 
         * @param handler the handler to use when execution is blocked because the thread bounds and queue capacities are
         *                reached
         * @return {@code this} builder instance
         */
        public FixedThreadPoolBuilder setRejectedExecutionHandler(final RejectedExecutionHandler handler) {
            requireNonNull(handler, "handler == null");
            this.handler = handler;
            return this;
        }

        /**
         * Sets the time limit for which threads may remain idle before being terminated.
         * 
         * @param duration the time limit for which threads may remain idle before being terminated
         * @throws ArithmeticException if duration is too large to fit in a {@code long} milliseconds value
         * @implNote the specified {@code duration} will converted into {@link TimeUnit#MILLISECONDS milliseconds}
         * @return {@code this} builder instance
         */
        public FixedThreadPoolBuilder setKeepAliveTime(final Duration duration) {
            requireNonNull(duration, "duration == null");
            this.keepAliveTime = duration.toMillis();
            return this;
        }

        /**
         * Builds a new {@code PausableThreadPoolExecutor} using the specified criteria. See {@link FixedThreadPoolBuilder} for
         * warnings and details.
         * 
         * @return a new {@code PausableThreadPoolExecutor} using the previously specified criteria
         */
        public PausableThreadPoolExecutor create() {
            return create(poolSize, poolSize, keepAliveTime, queue == null ? new LinkedBlockingQueue<>() : queue, factory, handler).setAllowCoreThreadTimeOut(keepAliveTime > 0);
        }

        protected static PausableThreadPoolExecutor create(final int corePoolSize, final int maxPoolSize, final long keepAliveTime, final BlockingQueue<Runnable> queue, final ThreadFactory factory, final RejectedExecutionHandler handler) {
            //@formatter:off
            return new PausableThreadPoolExecutor(corePoolSize,
                                                  maxPoolSize,
                                                  keepAliveTime,
                                                  TimeUnit.MILLISECONDS,
                                                  queue,
                                                  factory == null ? defaultThreadFactory() : factory,
                                                  handler == null ? new AbortPolicy()      : handler
                                );
            //@formatter:on
        }
    }

    /**
     * A builder which creates {@code PausableThreadPoolExecutor} instances which create new threads as needed.
     * <p>
     * <b>Warning: It is <u>strongly discouraged</u> to create a cached thread pool with the intention of using the
     * {@link PausableThreadPoolExecutor#pause() pause}/{@link PausableThreadPoolExecutor#resume() resume} functionality,
     * unless you have strict control over the maximum concurrency level outside the thread pool!</b> A thread in the pool
     * only becomes aware of a pause request in the {@link ThreadPoolExecutor#beforeExecute(Thread, Runnable) beforeExecute}
     * method, after it is created and assigned to execute a task from the {@link ThreadPoolExecutor#getQueue() work queue}.
     * In the presence of unlimited incoming tasks, a cached thread pool in a paused state will continue to create new
     * threads indefinitely as each thread is assigned a task and then paused before execution.
     * <p>
     * <b>It is only recommended to use a cached thread pool with a {@link BoundedExecutor} or
     * {@link BoundedExecutorService}.</b>
     * <p>
     * If the {@link #setWorkQueue(BlockingQueue) work queue}, {@link #setThreadFactory(ThreadFactory) thread factory},
     * {@link #setKeepAliveTime(Duration) keep-alive time}, and
     * {@link #setRejectedExecutionHandler(RejectedExecutionHandler) rejected execution handler} are left unspecified a
     * {@link SynchronousQueue}, {@link Executors#defaultThreadFactory() default thread factory},
     * {@code 60 milliseconds keep-alive time}, and {@link java.util.concurrent.ThreadPoolExecutor.AbortPolicy AbortPolicy}
     * will be used respectively.
     */
    public static final class CachedThreadPoolBuilder extends FixedThreadPoolBuilder {

        /**
         * Builds a new {@code PausableThreadPoolExecutor} using the specified criteria. See {@link CachedThreadPoolBuilder} for
         * warnings and details.
         * 
         * @return a new {@code PausableThreadPoolExecutor} using the previously specified criteria
         */
        public PausableThreadPoolExecutor create() {
            return create(0, Integer.MAX_VALUE, keepAliveTime == 0 ? 6000 : keepAliveTime, queue == null ? new SynchronousQueue<>() : queue, factory, handler);
        }

        // @formatter:off
        @Override public CachedThreadPoolBuilder setWorkQueue(final BlockingQueue<Runnable> queue) { return (CachedThreadPoolBuilder) super.setWorkQueue(queue); }
        @Override public CachedThreadPoolBuilder setThreadFactory(final ThreadFactory factory) { return (CachedThreadPoolBuilder) super.setThreadFactory(factory); }
        @Override public CachedThreadPoolBuilder setRejectedExecutionHandler(final RejectedExecutionHandler handler) { return (CachedThreadPoolBuilder) super.setRejectedExecutionHandler(handler); }
        @Override public CachedThreadPoolBuilder setKeepAliveTime(final Duration duration) { return (CachedThreadPoolBuilder) super.setKeepAliveTime(duration); }        
        // @formatter:on
    }

}