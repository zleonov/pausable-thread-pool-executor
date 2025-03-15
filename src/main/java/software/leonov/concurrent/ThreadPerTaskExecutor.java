package software.leonov.concurrent;

import static java.util.Objects.requireNonNull;

import java.lang.Thread.UncaughtExceptionHandler;
import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A lightweight {@code Executor} that generates a new {@code Thread} for each {@link #execute(Runnable) task}.
 * <p>
 * The number of threads that can be generated is unlimited. There is no work queue or {@link RejectedExecutionHandler
 * saturation policies} of any kind. Tasks are executed immediately. There is no error handling beyond that which the
 * tasks themselves can handle. All uncaught errors will be handled by the thread's {@link UncaughtExceptionHandler}.
 * <p>
 * While this class does not implement the {@link ExecutorService} interface, it does provide {@link #shutdown()},
 * {@link #shutdown(boolean)}, {@link #awaitTermination(Duration)}, and {@link #awaitTermination()} methods, along with
 * their {@link #isShutdown()} and {@link #isTerminated()} counterparts.
 * 
 * @author Zhenya Leonov
 */
public final class ThreadPerTaskExecutor implements Executor {

//    private final Set<Thread>   threads = new HashSet<>();
    private final Set<Thread>      threads = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private volatile ThreadFactory factory;

    private final AtomicInteger activeCount = new AtomicInteger();

    private final Lock      lock       = new ReentrantLock();
    private final Condition terminated = lock.newCondition();

    private static enum State {
        RUNNING, SHUTDOWN, TERMINATED
    }

    private volatile State state;

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
     * The number of threads that can be generated is unlimited. There is no work queue or {@link RejectedExecutionHandler
     * saturation policies} of any kind. Tasks are executed immediately. There is no error handling beyond that which the
     * tasks themselves can handle. All uncaught errors will be handled by the thread's
     * {@link Thread.UncaughtExceptionHandler UncaughtExceptionHandler}.
     * 
     * @throws RejectedExecutionException if an only if this {@code Executor} has been {@link #shutdown(boolean) shutdown}
     */
    @Override
    public void execute(final Runnable command) {
        requireNonNull(command, "command == null");

        if (state != State.RUNNING) // best effort to avoid creating unneeded threads
            throw new RejectedExecutionException();

        final ThreadFactory factory = this.factory;

        final Thread thread = factory.newThread(() -> {
            try {
                activeCount.incrementAndGet();
                command.run();
            } finally {
                activeCount.decrementAndGet();

                threads.remove(Thread.currentThread());

                if (state != State.RUNNING) {
                    lock.lock();
                    try {
                        if (state != State.TERMINATED)
                            tryTerminate();
                    } finally {
                        lock.unlock();
                    }
                }

            }
        });

        lock.lock();
        try {
            if (state != State.RUNNING)
                throw new RejectedExecutionException();
            threads.add(thread); // we are guaranteed at this point that the executor is not shutdown
        } finally {
            lock.unlock();
        }

        thread.start(); // since the thread has been added to the set this does not need to be in the lock block
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
     * Returns the number of currently executing tasks.
     * 
     * @return the number of currently executing tasks
     */
    public int getActiveCount() {
        return activeCount.get();
    }

    /**
     * Initiates a shutdown of this {@code Executor} after which no new tasks will be accepted.
     * <p>
     * Calling this method more than once is a no-op.
     * <p>
     * Active threads are not interrupted. This method delegates to {@link #shutdown(boolean) shutdown(false)}.
     * 
     * @return this {@link ThreadPerTaskExecutor} instance
     */
    public ThreadPerTaskExecutor shutdown() {
        return shutdown(false);
    }

    /**
     * Initiates a shutdown of this {@code Executor} after which no new tasks will be accepted.
     * <p>
     * Calling this method more than once is a no-op.
     *
     * @param interrupt whether or not to {@link Thread#interrupt() interrupt} all active threads
     * @return this {@link ThreadPerTaskExecutor} instance
     */
    public ThreadPerTaskExecutor shutdown(final boolean interrupt) {
        lock.lock();
        try {
            if (state == State.RUNNING) {
                state = State.SHUTDOWN;

                if (interrupt)
                    threads.forEach(Thread::interrupt);

                tryTerminate();
            }
        } finally {
            lock.unlock();
        }
        return this;
    }

    private void tryTerminate() {
        if (threads.isEmpty()) {
            state = State.TERMINATED;
            terminated.signalAll();
        }
    }

    /**
     * Returns whether or not this {@code Executor} has been {@link #shutdown(boolean) shutdown}.
     * 
     * @return {@code true} if this {@code Executor} has been {@link #shutdown(boolean) shutdown}
     */
    public boolean isShutdown() {
        return state != State.RUNNING;
    }

    /**
     * Returns whether or not this {@code Executor} has terminated.
     * 
     * @return whether or not this {@code Executor} has terminated
     */
    public boolean isTerminated() {
        return state == State.TERMINATED;
    }

    /**
     * Blocks until all tasks have completed execution after a {@link #shutdown(boolean) shutdown} request, the
     * {@code timeout} occurs, or the current thread is interrupted, whichever happens first.
     * 
     * @param timeout the maximum time to wait
     * @return {@code true} if this {@code Executor} has {@link #isTerminated() terminated} or {@code false} if the timeout
     *         expired
     * @throws ArithmeticException  if {@code duration} is too large to fit in a {@code long} nanoseconds value
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    public boolean awaitTermination(final Duration timeout) throws InterruptedException {
        requireNonNull(timeout, "timeout == null");
        long nanos = timeout.toNanos();

        lock.lock();
        try {
            while (state != State.TERMINATED) {
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
     * Returns a string representing the approximate state if this {@code Executor}.
     *
     * @return a string representing the approximate state if this {@code Executor}
     */
    @Override
    public String toString() {
        // we don't need to lock since this string representation is approximate and no possible guarantees are given or implied
        return this.getClass().getSimpleName() + " [state = " + state + ", activeCount = " + activeCount.get() + "]";
    }

}
