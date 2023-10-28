package software.leonov.concurrent;

import static java.util.Objects.requireNonNull;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A thread-safe blocking reference that allows threads to wait for a value to be set.
 * <p>
 * This class has similar semantics to {@link AtomicReference} but is designed for situations where synchronization is
 * necessary, and efficient use of system resources is a concern.
 *
 * @param <T> the type of value referred by this reference
 * 
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
        final T localValue = value;
        return localValue == null ? take() : localValue;
    }

    /**
     * Returns the current value, blocking indefinitely if the value is not set.
     *
     * @return the current value
     */
    public T getUninterruptibly() {
        final T localValue = value;
        return localValue == null ? takeUninterruptibly() : localValue;
    }

    /**
     * Returns the current value, blocking if the value is not set or the specified time elapses.
     *
     * @param duration the maximum time to wait for the value to be set
     * @return the current value
     * @throws ArithmeticException  if duration is too large to fit in a {@code long} nanoseconds value
     * @throws InterruptedException if the calling thread is interrupted while waiting
     * @throws NullPointerException if {@code duration} is {@code null}
     */
    public T get(final Duration duration) throws InterruptedException {
        requireNonNull(duration, "duration == null");

        final T localValue = value;
        return localValue == null ? take(duration) : localValue;
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
            while (value == null)
                condition.awaitNanos(duration.toNanos());
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
     * Clears the current value. to be set.
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
