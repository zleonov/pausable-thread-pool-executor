package software.leonov.concurrent;

import static java.util.Objects.requireNonNull;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * A synchronization aid similar to a {@link CountDownLatch} or a {@link Semaphore} but allows threads to count up and
 * down and wait until a specified target count is reached.
 * <p>
 * A {@code Counter} is {@link #Counter(int, int) initialized} with an initial and a target count. Threads can increment
 * the count using {@link #countUp()} and decrement it using {@link #countDown()}. The {@link #await()} method blocks
 * until the target count is reached.
 * <p>
 * <b>Warning: A race condition may occur if this class is initialized with an initial count equal to the target
 * count.</b> Awaiting threads may <i>fall through</i> the barrier before any worker thread has a chance to modify the
 * count. This <b>can lead to incorrect results if you are using this {@code Counter} to ensure that worker threads
 * complete their operations before releasing the waiting threads.</b>
 * <p>
 * This behavior may seem especially counter intuitive to users of {@link CountDownLatch} which is always initialized
 * with a count higher than zero, thus ensuring that at least one worker thread has a chance to modify the count before
 * a waiting thread is released.
 * <p>
 * A {@code Counter} is a versatile tool for various synchronization scenarios. It can be used as a bi-directional latch
 * allowing threads to wait until a specific count is reached in either direction.
 * 
 * @implNote This class handles the possibly of <i>spurious wakeups</i> internally
 * @author Zhenya Leonov
 */
public final class Counter implements Awaitable {

    private static final class Synchronizer extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = 744247383119520937L;

        private final int        target;
        private volatile boolean done;

        private Synchronizer(final int initial, final int target) {
            setState(initial);
            this.target = target;
        }

        public int count() {
            return getState();
        }

        @Override
        protected int tryAcquireShared(final int value) {
            return done ? 1 : -1;
        }

        @Override
        protected boolean tryReleaseShared(final int value) {
            while (true) {
                if (done)
                    return false;

                final int count = count();

                final int next = count + value;

                if (compareAndSetState(count, next))
                    return done |= next == target;
            }
        }
    }

    private final Synchronizer sync;

    /**
     * Constructs a {@code Counter} with the specified initial and target count. CountDownLatch
     *
     * @param initial the initial count
     * @param target  the target count
     */
    public Counter(final int initial, final int target) {
        this.sync = new Synchronizer(initial, target);
    }

    /**
     * Causes the current thread to wait until the target count is reached or the thread is {@link Thread#interrupt
     * interrupted}. If the current count is equal to the target count then this method returns immediately.
     * <p>
     * <b>Warning: A race condition may occur if this class is initialized with an initial count equal to the target
     * count.</b> A waiting thread may <i>fall through</i> the barrier before any worker thread has a chance to modify the
     * count. This behavior may appear unexpected when analyzing the code in a linear fashion and <b>can lead to incorrect
     * results if you are using this {@code Counter} to ensure that worker threads complete their operations before
     * releasing the waiting threads.</b>
     */
    @Override
    public void await() throws InterruptedException {
        sync.acquireSharedInterruptibly(1);
    }

    /**
     * Causes the current thread to wait until the target count is reached, unless the thread is {@link Thread#interrupt
     * interrupted}, or the specified time elapses. If the current count is equal to the target count then this method
     * returns immediately.
     * <p>
     * <b>Warning: A race condition may occur if this class is initialized with an initial count equal to the target
     * count.</b> A waiting thread may <i>fall through</i> the barrier before any worker thread has a chance to modify the
     * count. This behavior may appear unexpected when analyzing the code in a linear fashion and <b>can lead to incorrect
     * results if you are using this {@code Counter} to ensure that worker threads complete their operations before
     * releasing the waiting threads.</b>
     * <p>
     * This behavior may seem especially counter intuitive to users of {@link CountDownLatch} which is always initialized
     * with a count higher than zero, thus ensuring that at least one worker thread has a chance to modify the count before
     * a waiting thread is released.
     */
    @Override
    public boolean await(final Duration duration) throws InterruptedException {
        requireNonNull(duration, "duration == null");
        return sync.tryAcquireSharedNanos(1, duration.toNanos());
    }

    /**
     * Causes the current thread to wait indefinitely until the target count is reached. If the current count is equal to
     * the target count then this method returns immediately.
     * <p>
     * If the current thread is {@link Thread#interrupted() interrupted} while waiting, it will continue to wait until
     * signalled. When this method returns the thread's {@link Thread#isInterrupted() interrupted status} will still be set.
     * <p>
     * <b>Warning: A race condition may occur if this class is initialized with an initial count equal to the target
     * count.</b> A waiting thread may <i>fall through</i> the barrier before any worker thread has a chance to modify the
     * count. This behavior may appear unexpected when analyzing the code in a linear fashion and <b>can lead to incorrect
     * results if you are using this {@code Counter} to ensure that worker threads complete their operations before
     * releasing the waiting threads.</b>
     * <p>
     * This behavior may seem especially counter intuitive to users of {@link CountDownLatch} which is always initialized
     * with a count higher than zero, thus ensuring that at least one worker thread has a chance to modify the count before
     * a waiting thread is released.
     */
    @Override
    public void awaitUninterruptibly() {
        sync.acquireShared(1);
    }

    /**
     * Increments the count.
     */
    public void countUp() {
        sync.releaseShared(1);
    }

    /**
     * Decrements the count.
     */
    public void countDown() {
        sync.releaseShared(-1);
    }

    /**
     * Returns the current count.
     *
     * @return the current count
     */
    public int getCount() {
        return sync.count();
    }

    @Override
    public String toString() {
        return Counter.class.getSimpleName() + " [count = " + getCount() + "]";
    }

}