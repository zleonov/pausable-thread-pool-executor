package software.leonov.concurrent;

import static java.util.Objects.requireNonNull;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A thread-safe reference holder that allows threads to block until a value becomes available.
 * <p>
 * This class combines the features of an {@link AtomicReference}, a single-element {@link BlockingQueue}, and a
 * {@link BooleanLatch}. It's primarily designed for concurrent algorithms where one or more threads need to wait for an
 * initial resource before proceeding. Common use cases include background monitoring, logging, and maintenance threads
 * that need to wait for initialization before starting their main processing loop. A convenient and particularly useful
 * aid in scenarios where direct thread management is not available, such as when submitting tasks to an
 * {@code Executor} outside your control.
 * <p>
 * <b>Usage considerations:</b><br>
 * Once a value has been {@link #set} the {@link #get} operation becomes non-blocking and incurs only minimal
 * performance overhead, making this class well-suited for <i>Single-Producer-Multiple-Consumer (SPMC)</i> scenarios.
 * <p>
 * While this class is mutable via the {@link #clear}, {@link #set}, {@link #compareAndSet}, and {@link #getAndSet}
 * operations, these methods are included <i>only</i> for occasional use. Avoid attempts to use this class as a
 * general-purpose thread-safe single-element collection for both performance and design considerations.
 * 
 * @param <T> the type of value
 * @implNote This class manages the possibility of <i>spurious wakeups</i> internally.
 * @author Zhenya Leonov
 */
public final class BlockingReference<T> {

    private volatile T value;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();

    /**
     * Constructs a new {@code BlockingReference} with no initial value.
     */
    public BlockingReference() {
        value = null;
    }

    /**
     * Constructs a new {@code BlockingReference} with the specified initial value.
     *
     * @param initialValue the initial value
     * @throws NullPointerException if {@code initialValue} is {@code null}
     */
    public BlockingReference(final T initialValue) {
        requireNonNull(initialValue, "initialValue == null");
        value = initialValue;
    }

    /**
     * Returns the current value, blocking if the value is not set.
     *
     * @return the current value
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    public T get() throws InterruptedException {
        final T t = value;
        return t == null ? take() : t;
    }

    /**
     * Returns the current value, blocking indefinitely if the value is not set.
     *
     * @return the current value
     */
    public T getUninterruptibly() {
        final T t = value;
        return t == null ? takeUninterruptibly() : t;
    }

    /**
     * Returns an {@code Optional} containing the current value, blocking if the value is not set for the specified amount
     * of time.
     *
     * @param duration the maximum time to wait for the value to be set
     * @return an {@code Optional} containing the current value or an empty {@code Optional} if the timeout elapses before a
     *         value is set
     * @throws ArithmeticException  if duration is too large to fit in a {@code long} nanoseconds value
     * @throws InterruptedException if the calling thread is interrupted while waiting
     * @throws NullPointerException if {@code duration} is {@code null}
     */
    public Optional<T> get(final Duration duration) throws InterruptedException {
        requireNonNull(duration, "duration == null");

        final T t = value;
        // return localValue == null ? Optional.ofNullable(take(duration)) : Optional.of(localValue);
        return Optional.ofNullable(t == null ? take(duration) : t);
    }

    private T take() throws InterruptedException {
        lock.lock();
        try {
            while (value == null)
                condition.await();
            return value;
        } finally {
            lock.unlock();
        }
    }

    private T take(final Duration duration) throws InterruptedException {
        requireNonNull(duration, "duration == null");

        lock.lock();
        try {
            long nanos = duration.toNanos();
            while (value == null && nanos > 0L)
                nanos = condition.awaitNanos(nanos);
            return value;
        } finally {
            lock.unlock();
        }
    }

    private T takeUninterruptibly() {

        lock.lock();
        try {
            while (value == null)
                condition.awaitUninterruptibly();
            return value;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the specified value. When this method completes all waiting threads will be released.
     *
     * @param value the value to set
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public void set(final T value) {
        requireNonNull(value, "value == null");

        lock.lock();
        try {
            this.value = value;
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Compares the current value with the {@code expectedValue} and, if they are equal, sets the {@code newValue}. When
     * this method completes all waiting threads will be released.
     *
     * @param expectedValue the expected value
     * @param newValue      the value to set if the {@code expectedValue} matches the current value
     * @return {@code true} if the value was successfully updated, {@code false} if {@code expectedValue} does not match the
     *         current value
     * @throws NullPointerException if {@code expectedValue} or {@code newValue} is {@code null}
     */
    public boolean compareAndSet(final T expectedValue, final T newValue) {
        requireNonNull(expectedValue, "expectedValue == null");
        requireNonNull(newValue, "newValue == null");

        lock.lock();
        try {
            if (Objects.equals(value, expectedValue))
                return false;
            value = newValue;
            condition.signalAll();
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the {@code newValue} and returns an {@code Optional} containing the previous value.
     *
     * @param newValue the value to set
     * @return an {@code Optional} containing the previous value or an empty {@code Optional} if the value was not set
     * @throws NullPointerException if {@code newValue} is {@code null}
     */
    public Optional<T> getAndSet(final T newValue) {
        requireNonNull(newValue, "newValue == null");

        lock.lock();
        try {
            final T t = value;
            value = newValue;
            condition.signalAll();
            return Optional.ofNullable(t);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Clears the current value.
     */
    public void clear() {
        lock.lock();
        try {
            value = null;
        } finally {
            lock.unlock();
        }
    }

}
