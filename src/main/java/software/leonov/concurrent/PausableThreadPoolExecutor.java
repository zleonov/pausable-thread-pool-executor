package software.leonov.concurrent;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.Executors.defaultThreadFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
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
 * convenience methods. Instances of this class can be created via the
 * {@link #newThreadPool(int, int, Duration, BlockingQueue) newThreadPool}, {@link #newFixedThreadPool(int)
 * newFixedThreadPool} and {@link #newCachedThreadPool() newCachedThreadPool} builders. After creation this executor is
 * not re-configurable. The {@link #setCorePoolSize(int)}, {@link #setMaximumPoolSize(int)},
 * {@link #setKeepAliveTime(long, TimeUnit)}, {@link #setRejectedExecutionHandler(RejectedExecutionHandler)}, and
 * {@link #setThreadFactory(ThreadFactory)} methods are disabled.
 * <p>
 * <b>Pause/resume functionality</b><br>
 * This executor builds on the <i>extension example</i> provided in the documentation of {@link ThreadPoolExecutor}. The
 * {@link #pause()} and {@link #resume()} methods allow users to halt or resume processing of pending tasks (pausing the
 * executor does not affect actively executing tasks).
 * <p>
 * The executor can be paused at any point unless it is {@link #isShutdown() shutting down} and the {@link #getQueue()
 * task queue} is empty. Note that calling {@link #shutdown()} does not automatically resume the executor. To do that
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
 * functions which will be run when the executor is paused/resumed, before/after task execution, and when the executor
 * has terminated, respectively. Most commonly used for logging, statistics gathering, or to initialize
 * {@code ThreadLocal} variables.
 * <p>
 * This is a convenient alternative to the idiomatic pre Java 8 style of extending classes for the purpose of overriding
 * empty <i>hook</i> methods.
 * <p>
 * <b>Convenience methods</b><br>
 * The {@link #shutdownFast()} method is the middle ground between {@link #shutdown() shutdown()} and
 * {@link #shutdownNow()} and the {@link #awaitTermination()} method is a shorthand for
 * {@link #awaitTermination(long, TimeUnit) awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS)}.
 * 
 * @author Zhenya Leonov
 * @see RetryPolicy
 */
public final class PausableThreadPoolExecutor extends ThreadPoolExecutor implements PausableExecutorService {

    static final Consumer<?> DO_NOTHING_CONSUMER = r -> {
    };

    static final Runnable DO_NOTHING_RUNNABLE = () -> {
    };

    private static final BiConsumer<?, ?> DO_NOTHING_BI_CONSUMER = (r, t) -> {
    };

    private final ReentrantLock lock      = new ReentrantLock();
    private final Condition     condition = lock.newCondition();

    private boolean paused = false;

    @SuppressWarnings("unchecked")
    private Consumer<Runnable>              beforeExecute   = (Consumer<Runnable>) DO_NOTHING_CONSUMER;
    @SuppressWarnings("unchecked")
    private BiConsumer<Runnable, Throwable> afterExecute    = (BiConsumer<Runnable, Throwable>) DO_NOTHING_BI_CONSUMER;
    @SuppressWarnings("unchecked")
    private Consumer<Runnable>              beforePause     = (Consumer<Runnable>) DO_NOTHING_CONSUMER;
    @SuppressWarnings("unchecked")
    private Consumer<Runnable>              afterPause      = (Consumer<Runnable>) DO_NOTHING_CONSUMER;
    private Runnable                        afterTerminated = DO_NOTHING_RUNNABLE;

    private PausableThreadPoolExecutor(final int corePoolSize, final int maxPoolSize, final Duration keepAliveTime, final BlockingQueue<Runnable> queue, final ThreadFactory factory, final RejectedExecutionHandler handler) {
        super(corePoolSize, maxPoolSize, keepAliveTime.toMillis(), TimeUnit.MILLISECONDS, queue, factory, handler);
    }

    @Override
    protected void beforeExecute(final Thread t, final Runnable r) {
        super.beforeExecute(t, r);

        final Consumer<Runnable> beforeExecute = this.beforeExecute;
        final Consumer<Runnable> afterPause    = this.afterPause;

        lock.lock();
        try {
            if (paused) {
                beforePause.accept(r);
                while (paused)
                    condition.await();
                afterPause.accept(r);
            }
        } catch (final InterruptedException e) {
            afterPause.accept(r);
            t.interrupt();
        } finally {
            lock.unlock();
        }
        beforeExecute.accept(r);
    }

    @Override
    protected void afterExecute(final Runnable r, final Throwable t) {
        super.afterExecute(r, t);
        final BiConsumer<Runnable, Throwable> afterExecute = this.afterExecute;

//        lock.lock();
//        try {
//            if (isShutdown() && getQueue().isEmpty())
//                paused = false;
//        } finally {
//            lock.unlock();
//        }
        afterExecute.accept(r, t);
    }

    @Override
    public boolean isPaused() {
        lock.lock();
        try {
            return isTerminated() ? false : paused;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean pause() {
        lock.lock();
        try {
            if (!paused)
                /*
                 * A potential race condition exists when a pause request occurs simultaneously with the ThreadPoolExecutor shutting
                 * down and processing its final task.
                 *
                 * If the last task was just handed off to a thread for execution, getQueue().isEmpty() will return true. However, if
                 * that thread hasn't yet tried to acquire the lock in beforeExecute(), the call to lock.hasQueuedThreads() will also
                 * return false. This suggest that there are no more remaining tasks and this method would return false, when
                 * theoretically we could still pause the last task since it hasn't begun execution.
                 *
                 * This race condition is benign for several reasons:
                 *
                 * 1) Tasks can only be paused in beforeExecute() *prior* (but not during) execution.
                 *
                 * 2) Users have no visibility or control over the specific timing between a task's removal from the queue and its
                 * actual execution in a thread.
                 *
                 * 3) No guarantees implied or explicit can exist regarding which thread will be the first to respond to a pause
                 * request.
                 *
                 * From the user's perspective, if this race condition occurs, it simply means the pause request arrived immediately
                 * after the last task's execution began, making it too late to pause.
                 */
                paused = !isShutdown() || !getQueue().isEmpty() || lock.hasQueuedThreads();
            return paused;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void resume() {
        lock.lock();
        try {
            if (paused) {
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

        final Runnable afterTerminated = this.afterTerminated;

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
     * Provides similar functionality to overriding {@link ThreadPoolExecutor#afterExecute(Runnable, Throwable)}. See the
     * {@code afterExecute} documentation for notes on differentiating {@code FutureTask}s from generic {@code Runnable}s.
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

//    /**
//     * A handler for rejected tasks that blocks the calling thread until there is an open slot in the
//     * {@link ThreadPoolExecutor#getQueue() task queue}.
//     * <p>
//     * Throws a {@link RejectedExecutionException} if the executor {@link #isShutdown is shutdown} or the calling thread is
//     * {@link Thread#interrupt interrupted}, in the latter case setting the thread's {@link Thread#isInterrupted()
//     * interrupted status} to {@code true}.
//     * 
//     * @author Zhenya Leonov
//     */
//    public static class CallerBlocksPolicy implements RejectedExecutionHandler {
//
//        private final Runnable before;
//        private final Runnable after;
//
//        /**
//         * Creates a new {@code CallerBlocksPolicy}.
//         */
//        public CallerBlocksPolicy() {
//            this(DO_NOTHING_RUNNABLE, DO_NOTHING_RUNNABLE);
//        }
//
//        /**
//         * Creates a new {@code CallerBlocksPolicy}.
//         * 
//         * @param before the runnable to run immediately before waiting for space to become available in the task queue (usually
//         *               used for debugging and logging)
//         * @param after  the runnable to run immediately after the task has been inserted into the task queue (usually used for
//         *               debugging and logging)
//         */
//        public CallerBlocksPolicy(final Runnable before, final Runnable after) {
//            requireNonNull(before, "before == null");
//            requireNonNull(after, "after == null");
//            this.before = before;
//            this.after = after;
//
//        }
//
//        @Override
//        public void rejectedExecution(final Runnable r, final ThreadPoolExecutor executor) {
//            if (executor.isShutdown())
//                throw new RejectedExecutionException();
//
//            try {
//                before.run();
//                executor.getQueue().put(r);
//                after.run();
//
//                if (executor.isShutdown() && executor.getQueue().remove(r))
//                    throw new RejectedExecutionException();
//            } catch (final InterruptedException ie) {
//                Thread.currentThread().interrupt();
//                throw new RejectedExecutionException(ie);
//            }
//        }
//    }

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
    public void setThreadFactory(ThreadFactory factory) {
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
     * Returns a {@link FixedThreadPoolBuilder builder} which creates {@code PausableThreadPoolExecutor}s that use a fixed
     * number of threads is equal to the {@link Runtime#availableProcessors() availableProcessors} - {@code 1}.
     * 
     * @return a {@link FixedThreadPoolBuilder builder} which creates {@code PausableThreadPoolExecutor}s that use a fixed
     *         number of threads is equal to the {@link Runtime#availableProcessors() availableProcessors} - {@code 1}
     */
    public static FixedThreadPoolBuilder newFixedThreadPool() {
        return new FixedThreadPoolBuilder(Runtime.getRuntime().availableProcessors() - 1);
    }

    /**
     * Returns a {@link FixedThreadPoolBuilder builder} which creates {@code PausableThreadPoolExecutor}s that use a fixed
     * number of threads.
     * 
     * @param nthreads the size of the pool
     * @return a {@link FixedThreadPoolBuilder builder} which creates {@code PausableThreadPoolExecutor}s that use a fixed
     *         number of threads
     */
    public static FixedThreadPoolBuilder newFixedThreadPool(final int nthreads) {
        if (nthreads < 1)
            throw new IllegalArgumentException("nthreads < 1");
        return new FixedThreadPoolBuilder(nthreads);
    }

    /**
     * Returns a {@link FixedThreadPoolBuilder builder} which creates {@code PausableThreadPoolExecutor}s which use a single
     * worker thread.
     * 
     * @return a {@link FixedThreadPoolBuilder builder} which creates {@code PausableThreadPoolExecutor}s which use a single
     *         worker thread
     */
    public static FixedThreadPoolBuilder newSingleThreadPool() {
        return new FixedThreadPoolBuilder(1);
    }

    /**
     * Returns a {@link CachedThreadPoolBuilder builder} which creates {@code PausableThreadPoolExecutor}s that generate new
     * threads as needed, but will reuse previously constructed threads when they are available.
     * <p>
     * <b>Warning: It is <u>strongly discouraged</u> to create a cached thread pool with the intention of using the
     * {@link PausableThreadPoolExecutor#pause() pause}/{@link PausableThreadPoolExecutor#resume() resume} functionality,
     * unless you have strict control over the maximum concurrency level of the thread pool.</b> A thread in the pool only
     * becomes aware of a pause request in the {@link ThreadPoolExecutor#beforeExecute(Thread, Runnable) beforeExecute}
     * method, after it is created and assigned to execute a task from the {@link ThreadPoolExecutor#getQueue() task queue}.
     * In the presence of unlimited incoming tasks, a cached thread pool in a paused state will continue to create new
     * threads indefinitely as each thread is assigned a task and then paused before execution.
     * <p>
     * <b>It is only recommended to use a cached thread pool with a {@link BoundedExecutor} or
     * {@link BoundedExecutorService}.</b>
     * 
     * @return a {@link CachedThreadPoolBuilder builder} which creates {@code PausableThreadPoolExecutor}s that generate new
     *         threads as needed
     */
    public static CachedThreadPoolBuilder newCachedThreadPool() {
        return new CachedThreadPoolBuilder();
    }

    /**
     * Returns a {@link ThreadPoolBuilder builder} which creates custom {@code PausableThreadPoolExecutor}s.
     * <p>
     * <b>Warning: It is <u>strongly discouraged</u> to create a thread pool which can generate a large or unlimited number
     * of threads with the intention of using the {@link PausableThreadPoolExecutor#pause()
     * pause}/{@link PausableThreadPoolExecutor#resume() resume} functionality, unless you have strict control over the
     * maximum concurrency level of the thread pool.</b> A thread in the pool only becomes aware of a pause request in the
     * {@link ThreadPoolExecutor#beforeExecute(Thread, Runnable) beforeExecute} method, after it is created and assigned to
     * execute a task from the {@link ThreadPoolExecutor#getQueue() task queue}. In the presence of unlimited incoming
     * tasks, a cached thread pool in a paused state will continue to create new threads indefinitely as each thread is
     * assigned a task and then paused before execution.
     * <p>
     * <b>It is only recommended to use a thread pool that can create a large or unlimited number of threads with a
     * {@link BoundedExecutor} or {@link BoundedExecutorService}.</b>
     * 
     * @param corePoolSize    the number of threads to keep in the pool, even if they are idle, unless
     *                        {@link ThreadPoolBuilder#coreThreadsTimeOut(boolean) allowCoreThreadTimeOut} is set to
     *                        {@code true}
     * @param maximumPoolSize the maximum number of threads to allow in the pool
     * @param keepAliveTime   the maximum time excess threads will wait for new tasks before terminating
     * @param queue           the task queue
     * @return a {@link ThreadPoolBuilder builder} which creates custom {@code PausableThreadPoolExecutor}s
     */
    public static ThreadPoolBuilder newThreadPool(final int corePoolSize, final int maximumPoolSize, final Duration keepAliveTime, final BlockingQueue<Runnable> queue) {
        if (corePoolSize < 0)
            throw new IllegalArgumentException("corePoolSize < 0");
        if (maximumPoolSize < 1)
            throw new IllegalArgumentException("maximumPoolSize < 1");
        if (maximumPoolSize < corePoolSize)
            throw new IllegalArgumentException("maximumPoolSize < corePoolSize");

        requireNonNull(keepAliveTime, "keepAliveTime == null");
        requireNonNull(queue, "queue == null");

        return new ThreadPoolBuilder(corePoolSize, maximumPoolSize, keepAliveTime, queue);
    }

    /**
     * A builder which creates custom {@code PausableThreadPoolExecutor}s.
     * <p>
     * <b>Warning: It is <u>strongly discouraged</u> to create a thread pool which can create a large or unlimited number of
     * threads with the intention of using the {@link PausableThreadPoolExecutor#pause()
     * pause}/{@link PausableThreadPoolExecutor#resume() resume} functionality, unless you have strict control over the
     * maximum concurrency level of the thread pool.</b> A thread in the pool only becomes aware of a pause request in the
     * {@link ThreadPoolExecutor#beforeExecute(Thread, Runnable) beforeExecute} method, after it is created and assigned to
     * execute a task from the {@link ThreadPoolExecutor#getQueue() task queue}. In the presence of unlimited incoming
     * tasks, a cached thread pool in a paused state will continue to create new threads indefinitely as each thread is
     * assigned a task and then paused before execution.
     * <p>
     * <b>It is only recommended to use a thread pool that can create a large or unlimited number of threads with a
     * {@link BoundedExecutor} or {@link BoundedExecutorService}.</b>
     * <p>
     * Unless otherwise specified core threads will never expire, and the {@link Executors#defaultThreadFactory() default
     * thread factory} and {@link java.util.concurrent.ThreadPoolExecutor.AbortPolicy AbortPolicy} will be used as the
     * {@link ThreadPoolExecutor#getThreadFactory() thread factory} and the
     * {@link ThreadPoolExecutor#getRejectedExecutionHandler() rejected execution handler}, respectively.
     */
    public static final class ThreadPoolBuilder extends AbstractThreadPoolBuilder {

        private final BlockingQueue<Runnable> queue;
        private final Duration                keepAliveTime;
        private boolean                       allowCoreThreadTimeOut = false;

        ThreadPoolBuilder(final int corePoolSize, final int maximumPoolSize, final Duration keepAliveTime, final BlockingQueue<Runnable> queue) {
            super(corePoolSize, maximumPoolSize);
            this.keepAliveTime = keepAliveTime;
            this.queue         = queue;
        }

        @Override
        public PausableThreadPoolExecutor create() {
            return new PausableThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, queue, factory != null ? factory : defaultThreadFactory(), handler != null ? handler : new AbortPolicy())
                    .setAllowCoreThreadTimeOut(allowCoreThreadTimeOut);
        }

        /**
         * Controls whether core threads can expire and terminate when idle. If enabled, core threads will terminate after the
         * keep-alive time if no tasks are available, and new threads will be created when tasks arrive.
         * <p>
         * Core threads normally remain alive indefinitely. Setting this to {@code true} applies the standard keep-alive timeout
         * to all threads, both core and excess threads. The keep-alive duration must be positive when enabling this feature to
         * avoid continual thread replacement.
         *
         * @param allow {@code true} to enable core thread expiration or {@code false} to keep core threads alive indefinitely
         * @return {@code this} builder instance
         */
        public ThreadPoolBuilder coreThreadsTimeOut(final boolean allow) {
            if (allow && keepAliveTime.isZero())
                throw new IllegalArgumentException("core threads must have non-zero keep-alive time");

            this.allowCoreThreadTimeOut = allow;
            return this;
        }

        @Override
        public ThreadPoolBuilder setThreadFactory(final ThreadFactory factory) {
            return (ThreadPoolBuilder) super.setThreadFactory(factory);
        }

        @Override
        public ThreadPoolBuilder setRejectedExecutionHandler(final RejectedExecutionHandler handler) {
            return (ThreadPoolBuilder) super.setRejectedExecutionHandler(handler);
        }

    }

    /**
     * A builder which creates {@code PausableThreadPoolExecutor}s that generate new threads as needed, but will reuse
     * previously constructed threads when they are available.
     * <p>
     * A {@link SynchronousQueue} will be used as the task queue, with idle threads expiring in 60 seconds. Unless otherwise
     * specified the {@link Executors#defaultThreadFactory() default thread factory} and
     * {@link java.util.concurrent.ThreadPoolExecutor.AbortPolicy AbortPolicy} will be used as the
     * {@link ThreadPoolExecutor#getThreadFactory() thread factory} and the
     * {@link ThreadPoolExecutor#getRejectedExecutionHandler() rejected execution handler}, respectively.
     * <p>
     * <b>Warning: It is <u>strongly discouraged</u> to create a cached thread pool with the intention of using the
     * {@link PausableThreadPoolExecutor#pause() pause}/{@link PausableThreadPoolExecutor#resume() resume} functionality,
     * unless you have strict control over the maximum concurrency level of the thread pool.</b> A thread in the pool only
     * becomes aware of a pause request in the {@link ThreadPoolExecutor#beforeExecute(Thread, Runnable) beforeExecute}
     * method, after it is created and assigned to execute a task from the {@link ThreadPoolExecutor#getQueue() task queue}.
     * In the presence of unlimited incoming tasks, a cached thread pool in a paused state will continue to create new
     * threads indefinitely as each thread is assigned a task and then paused before execution.
     * <p>
     * <b>It is only recommended to use a cached thread pool with a {@link BoundedExecutor} or
     * {@link BoundedExecutorService}.</b>
     */
    public static final class CachedThreadPoolBuilder extends AbstractThreadPoolBuilder {

        CachedThreadPoolBuilder() {
            super(0, Integer.MAX_VALUE);
        }

        @Override
        public PausableThreadPoolExecutor create() {
            return new PausableThreadPoolExecutor(corePoolSize, maximumPoolSize, Duration.ofSeconds(60), new SynchronousQueue<>(), factory != null ? factory : defaultThreadFactory(), handler != null ? handler : new AbortPolicy());
        }

        @Override
        public CachedThreadPoolBuilder setThreadFactory(final ThreadFactory factory) {
            return (CachedThreadPoolBuilder) super.setThreadFactory(factory);
        }

        @Override
        public CachedThreadPoolBuilder setRejectedExecutionHandler(final RejectedExecutionHandler handler) {
            return (CachedThreadPoolBuilder) super.setRejectedExecutionHandler(handler);
        }
    }

    /**
     * A builder which creates {@code PausableThreadPoolExecutor}s that use a fixed number of threads.
     * <p>
     * Threads never expire and unless otherwise specified a {@link LinkedBlockingQueue},
     * {@link Executors#defaultThreadFactory() default thread factory}, and
     * {@link java.util.concurrent.ThreadPoolExecutor.AbortPolicy AbortPolicy} will be used as the
     * {@link ThreadPoolExecutor#getQueue() task queue}, {@link ThreadPoolExecutor#getThreadFactory() thread factory}, and
     * the {@link ThreadPoolExecutor#getRejectedExecutionHandler() rejected execution handler}, respectively.
     */
    public static class FixedThreadPoolBuilder extends AbstractThreadPoolBuilder {

        private BlockingQueue<Runnable> queue = null;

        FixedThreadPoolBuilder(final int nthreads) {
            super(nthreads);
        }

        /**
         * Sets the queue to use for holding tasks before they are executed.
         * 
         * @param queue the queue to use for holding tasks before they are executed
         * @return {@code this} builder instance
         */
        public FixedThreadPoolBuilder setQueue(final BlockingQueue<Runnable> queue) {
            requireNonNull(queue, "queue == null");
            this.queue = queue;
            return this;
        }

        @Override
        public PausableThreadPoolExecutor create() {
            return new PausableThreadPoolExecutor(corePoolSize, maximumPoolSize, Duration.ZERO, queue != null ? queue : new LinkedBlockingQueue<>(), factory != null ? factory : defaultThreadFactory(),
                    handler != null ? handler : new AbortPolicy());
        }

        @Override
        public FixedThreadPoolBuilder setThreadFactory(final ThreadFactory factory) {
            return (FixedThreadPoolBuilder) super.setThreadFactory(factory);
        }

        @Override
        public FixedThreadPoolBuilder setRejectedExecutionHandler(final RejectedExecutionHandler handler) {
            return (FixedThreadPoolBuilder) super.setRejectedExecutionHandler(handler);
        }

    }

    /**
     * The base skeletal implementation of a thread pool builder.
     */
    public static abstract class AbstractThreadPoolBuilder {

        protected final int corePoolSize;
        protected final int maximumPoolSize;

        protected ThreadFactory            factory = null;
        protected RejectedExecutionHandler handler = null;

        /**
         * Builds a new {@code PausableThreadPoolExecutor} using the previously specified criteria.
         * 
         * @return a new {@code PausableThreadPoolExecutor} using the previously specified criteria
         */
        public abstract PausableThreadPoolExecutor create();

        AbstractThreadPoolBuilder(final int corePoolSize) {
            this.corePoolSize    = corePoolSize;
            this.maximumPoolSize = corePoolSize;
        }

        AbstractThreadPoolBuilder(final int corePoolSize, final int maximumPoolSize) {
            this.corePoolSize    = corePoolSize;
            this.maximumPoolSize = maximumPoolSize;
        }

        /**
         * Sets the factory to use when the executor creates a new thread.
         * 
         * @param factory the factory to use when the executor creates a new thread
         * @return {@code this} builder instance
         */
        public AbstractThreadPoolBuilder setThreadFactory(final ThreadFactory factory) {
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
        public AbstractThreadPoolBuilder setRejectedExecutionHandler(final RejectedExecutionHandler handler) {
            requireNonNull(handler, "handler == null");
            this.handler = handler;
            return this;
        }
    }

}