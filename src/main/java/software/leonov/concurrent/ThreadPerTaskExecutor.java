package software.leonov.concurrent;

import static java.util.Objects.requireNonNull;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A lightweight {@code ExecutorService} that generates a new {@code Thread} for each {@link #execute(Runnable) task}.
 * <p>
 * The number of threads that can be generated is unlimited. There is no {@link ThreadPoolExecutor#getQueue() work
 * queue} or {@link RejectedExecutionHandler saturation policies} of any kind. Tasks are executed immediately.
 * <p>
 * <b>Discussion:</b> {@code ThreadPerTaskExecutor} is useful when migrating legacy (pre Java 5) or custom
 * multi-threaded code to {@code java.util.concurrent}. It allows users to avoid common hazards and pitfalls (such as
 * improper use of thread-local variables) that can occur with {@code Executor}s which reuse threads, e.g.
 * {@link Executors#newCachedThreadPool() cached} or {@link Executors#newFixedThreadPool(int) fixed} variants of
 * {@code ThreadPoolExecutor}.
 * <p>
 * <b>Performance:</b> While thread creation is relatively expensive, typical performance should be commensurate to a
 * {@code ThreadPoolExecutor} when executing medium to long running tasks.
 * <p>
 * 
 * @author Zhenya Leonov
 */
public final class ThreadPerTaskExecutor extends AbstractExecutorService {

    private static enum State {
        RUNNING, SHUTDOWN, TERMINATED
    }

    private final Set<Thread> activeThreads = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Lock        lock          = new ReentrantLock();
    private final Condition   terminated    = lock.newCondition();

    private volatile ThreadFactory factory;
    private volatile State         state;
    private volatile AtomicLong    completedTasks = new AtomicLong(0);

    /**
     * Creates a new {@link ThreadPerTaskExecutor} which will use the {@link Executors#defaultThreadFactory() default thread
     * factory} to generate new threads.
     */
    public ThreadPerTaskExecutor() {
        this(Executors.defaultThreadFactory());
    }

    /**
     * Creates a new {@link ThreadPerTaskExecutor} which will use the specified thread {@code ThreadFactory} to generate new
     * threads.
     *
     * @param factory the specified {@link ThreadFactory}
     */
    public ThreadPerTaskExecutor(final ThreadFactory factory) {
        requireNonNull(factory, "factory == null");
        this.factory = factory;
        state        = State.RUNNING;
    }

    /**
     * Executes the given command in a new thread.
     * <p>
     * The number of threads that can be generated is unlimited. There is no task queue or {@link RejectedExecutionHandler
     * saturation policies} of any kind. Tasks are executed immediately.
     * 
     * @throws RejectedExecutionException if and only if this {@code ExecutorService} has been {@link #shutdown(boolean)
     *                                    shutdown}
     */
    @Override
    public void execute(final Runnable command) {
        requireNonNull(command, "command == null");

        if (isShutdown()) // Best effort attempt to avoid creating unnecessary threads after a shutdown request.
            throw new RejectedExecutionException();

        final ThreadFactory factory = this.factory;

        final Thread thread = factory.newThread(() -> {
            try {
                command.run();
            } finally {
                activeThreads.remove(Thread.currentThread());
                completedTasks.incrementAndGet();

                if (isShutdown()) // Since variable assignment is atomic we don't need to be in a lock block
                    lock(() -> {
                        /*
                         * In case we are one of the last threads after shutdown
                         */
                        tryTerminate();
                    });
            }
        });

        lock(() -> {
            if (isShutdown())
                throw new RejectedExecutionException();
            activeThreads.add(thread); // We are guaranteed at this point that the executor is not shutdown
        });

        /*
         * since we define an "active" thread as any thread that has been added to activeThreads, we are free to invoke start()
         * from outside the lock block.
         */
        thread.start();

    }

    /**
     * Sets the {@code ThreadFactory} used to generate new {@code Thread}s.
     *
     * @param factory the specified {@code ThreadFactory}
     * @return this {@link ThreadPerTaskExecutor} instance
     */
    public ThreadPerTaskExecutor setThreadFactory(final ThreadFactory factory) {
        requireNonNull(factory, "factory == null");
        this.factory = factory;
        return this;
    }

    /**
     * Returns the {@code ThreadFactory} used to generate new {@code Thread}s.
     * 
     * @return the {@code ThreadFactory} used to generate new {@code Thread}s
     */
    public ThreadFactory getThreadFactory() {
        return factory;
    }

    /**
     * Returns the approximate number of threads which are currently executing tasks.
     * 
     * @return the approximate number of threads which are currently executing tasks
     */
    public int getActiveCount() {
        return activeThreads.size();
    }

    public long getCompletedTaskCount() {
        return completedTasks.get();
    }

    /**
     * Initiates a shutdown of this {@code ExecutorService} after which no new tasks will be accepted.
     * <p>
     * Calling this method more than once is a no-op.
     */
    public void shutdown() {
        shutdown(false);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This method always returns an empty list since tasks are executed immediately and are never pending.
     */
    @Override
    public List<Runnable> shutdownNow() {
        shutdown(true);
        return Collections.emptyList();
    }

    private void shutdown(final boolean interrupt) {
        lock(() -> {
            if (state == State.RUNNING) {
                state = State.SHUTDOWN;

                if (interrupt)
                    activeThreads.forEach(Thread::interrupt);

                /*
                 * if no threads are active we terminate immediately on the shutdown request. Otherwise one of the last active threads
                 * will terminate.
                 */
                tryTerminate();
            }
        });
    }

    /**
     * Returns whether or not this {@code ExecutorService} has been {@link #shutdown(boolean) shutdown}.
     * 
     * @return {@code true} if this {@code ExecutorService} has been {@link #shutdown(boolean) shutdown}
     */
    public boolean isShutdown() {
        return state != State.RUNNING;
    }

    /**
     * Returns whether or not this {@code ExecutorService} has terminated.
     * 
     * @return whether or not this {@code ExecutorService} has terminated
     */
    public boolean isTerminated() {
        return state == State.TERMINATED;
    }

    /**
     * Blocks until all tasks have completed execution after a shutdown request, or the timeout occurs, or the current
     * thread is interrupted, whichever happens first.
     * <p>
     * This method is a simple overload of {@link #awaitTermination(long, TimeUnit)} allowing users to specify the timeout
     * more conveniently using a {@link Duration} argument.
     * 
     * @param timeout the maximum time to wait
     * @return {@code true} if this executor has {@link #isTerminated() terminated} or {@code false} if the timeout expired
     * @throws ArithmeticException  if {@code duration} is too large to fit in a {@code long} nanoseconds value
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    public boolean awaitTermination(final Duration timeout) throws InterruptedException {
        requireNonNull(timeout, "timeout == null");
        return awaitTermination(timeout.toNanos(), TimeUnit.NANOSECONDS);
    }

    @Override
    public boolean awaitTermination(final long timeout, final TimeUnit unit) throws InterruptedException {
        requireNonNull(unit, "unit == null");

        long nanos = unit.toNanos(timeout);

        lock.lock();
        try {
            while (!isTerminated()) {
                if (nanos <= 0)
                    return false;
                nanos = terminated.awaitNanos(nanos); // we are in a loop so we are not worried about spurious wakeups
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Blocks indefinitely until all tasks have completed execution after a {@link #shutdown(boolean) shutdown} request
     * <b>or the current thread is interrupted</b>.
     * <p>
     * This call is equivalent to {@code awaitTermination(Duration.ofNanos(Long.MAX_VALUE))}.
     * <p>
     * Use this method only if you are sure that all tasks will eventually succeed in a reasonable amount of time.
     * 
     * @throws ArithmeticException  if {@code duration} is too large to fit in a {@code long} nanoseconds value
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    public void awaitTermination() throws InterruptedException {
        awaitTermination(Duration.ofNanos(Long.MAX_VALUE));
    }

    /**
     * Returns a string representing the approximate state if this {@code ExecutorService}.
     *
     * @return a string representing the approximate state if this {@code ExecutorService}
     */
    @Override
    public String toString() {
        // we don't need to lock since this string representation is approximate and no possible guarantees are given or implied
        return this.getClass().getSimpleName() + " [state = " + state + ", active threads = " + activeThreads.size() + "]";
    }

    private void tryTerminate() {
        // this method should always be called in a lock block
        if (!isTerminated() && activeThreads.isEmpty()) {
            state = State.TERMINATED;
            terminated.signalAll();
        }
    }

    // for purposes of cute code
    private void lock(final Runnable r) {
        lock.lock();
        try {
            r.run();
        } finally {
            lock.unlock();
        }
    }

}