package software.leonov.concurrent;

import static java.util.Objects.requireNonNull;
import static software.leonov.concurrent.PausableThreadPoolExecutor.DO_NOTHING_CONSUMER;
import static software.leonov.concurrent.PausableThreadPoolExecutor.DO_NOTHING_RUNNABLE;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy;
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy;
import java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy;
import java.util.concurrent.ThreadPoolExecutor.DiscardPolicy;
import java.util.function.Consumer;

/**
 * A handler for rejected tasks that attempts to retry {@link ExecutorService#execute(Runnable) execution} (in the
 * calling thread), unless the executor has been {@link ExecutorService#isShutdown() shutdown}, in which case it throws
 * an {@link RejectedExecutionException}.
 * <p>
 * <b>Discussion:</b><br>
 * Java provides 4 <i>saturation policies</i> to handle rejected tasks: {@link AbortPolicy} (default),
 * {@link CallerRunsPolicy}, {@link DiscardPolicy}, and {@link DiscardOldestPolicy} when using a
 * {@code ThreadPoolExecutor} with a bounded {@link ThreadPoolExecutor#getQueue() work queue}. There is no predefined
 * saturation policy to make the caller block when the queue is full. {@code CallerRunsPolicy} provides the closest
 * functionality, executing the rejected task in the thread which tried to submit the task, de facto throttling task
 * submission.
 * <p>
 * {@code RetryPolicy} offers advantages over {@code CallerRunsPolicy} in several scenarios:
 * <ul>
 * <li>When it's acceptable but not preferable to reject a long running task: {@code RetryPolicy} can be configured to
 * use {@link Builder#setInitialDelay(Duration) constant} delay or an
 * <a href="https://en.wikipedia.org/wiki/Exponential_backoff" target="_blank">exponential backoff</a>
 * {@link Builder#setMultiplier(double) strategy} between retries, with an optional
 * {@link Builder#setMaxTimeout(Duration) maximum timeout}.
 * <li>When you want to throttle task submission but avoid executing rejected tasks on the {@link CallerRunsPolicy
 * caller thread}: {@code RetryPolicy} offers a conceptually <i>cleaner</i> solution by ensuring that all tasks execute
 * <i>within</i> the thread pool. For example if you want all tasks to execute using a
 * {@link ThreadPoolExecutor#setThreadFactory(java.util.concurrent.ThreadFactory) custom thread factory}, or to simplify
 * troubleshooting by avoiding scenarios where the main dispatcher thread suddenly begins executing tasks directly,
 * making it harder to trace the execution flow in debug logs.
 * </ul>
 * <p>
 * Instances of this policy are created using a {@link RetryPolicy.Builder builder} obtained from
 * {@link #setMaxRetries(int)}:
 * <p>
 * <pre>{@code
 *   RetryPolicy.setMaxRetries(...)
 *              .setInitialDelay(Duration.ofMillis(...))
 *              .setMaxDelay(Duration.ofSeconds(...))
 *              .setMaxTimeout(Duration.ofMinutes(...))
 *              .setMultiplier(...)
 *              .onDelay(delay -> ...)
 *              .onSuccess(() -> ...)
 *              .create();}</pre>
 * 
 * @author Zhenya Leonov
 */
public class RetryPolicy implements RejectedExecutionHandler {

    private final int    maxRetries;
    private final long   initialDelayMillis;
    private final long   maxDelayMillis;
    private long         maxTimeoutMillis;
    private final double multiplier;

    private final Consumer<Duration> delay;
    private final Runnable           success;

    private RetryPolicy(final int maxRetries, final long initialDelayMillis, final long maxDelayMillis, final long maxTimeoutMillis, final double multiplier, final Consumer<Duration> delay, final Runnable success) {
        this.maxRetries         = maxRetries;
        this.initialDelayMillis = initialDelayMillis;
        this.maxDelayMillis     = maxDelayMillis;
        this.maxTimeoutMillis   = maxTimeoutMillis;
        this.multiplier         = multiplier;
        this.delay              = delay;
        this.success            = success;
    }

    /**
     * A builder of {@code RetryPolicy} instances.
     * <p>
     * Unless otherwise specified the builder will be {@link Builder#create() configured} with the
     * {@link Builder#setInitialDelay(Duration) initial delay interval} set to {@code 500ms}, no maximum
     * {@link Builder#setMaxDelay(Duration) delay interval} or {@link Builder#setMaxTimeout(Duration) timeout}, and the
     * <a href="https://en.wikipedia.org/wiki/Exponential_backoff" target="_blank">exponential backoff</a>
     * {@link Builder#setMultiplier(double) multiplier} set at {@code 2}.
     */
    public static class Builder {
        private final int maxRetries;
        private long      initialDelayMillis = 500;
        private long      maxDelayMillis     = Long.MAX_VALUE;
        private long      maxTimeoutMillis   = Long.MAX_VALUE;
        private double    multiplier         = 2;

        private Runnable           success = DO_NOTHING_RUNNABLE;
        @SuppressWarnings("unchecked")
        private Consumer<Duration> delay   = (Consumer<Duration>) DO_NOTHING_CONSUMER;

        private Builder(final int maxRetries) {
            if (maxRetries < 0)
                throw new IllegalArgumentException("maxRetries < 0");
            this.maxRetries = maxRetries;
        }

        /**
         * Sets the initial delay interval.
         * 
         * @param initialDelay the initial delay interval
         * @return this {@code Builder}
         */
        public Builder setInitialDelay(final Duration initialDelay) {
            requireNonNull(initialDelay, "initialDelay == null");
            if (initialDelay.isNegative())
                throw new IllegalArgumentException("initialDelay < 0");
            this.initialDelayMillis = initialDelay.toMillis();
            return this;
        }

        /**
         * Sets the maximum delay interval.
         * 
         * @param maxDelay the maximum delay interval
         * @return this {@code Builder}
         */
        public Builder setMaxDelay(final Duration maxDelay) {
            requireNonNull(maxDelay, "maxDelay == null");
            if (maxDelay.isNegative())
                throw new IllegalArgumentException("maxDelay < 0");
            final long millis = maxDelay.toMillis();
            if (initialDelayMillis > millis)
                throw new IllegalArgumentException("initialDelay > maxDelay");
            this.maxDelayMillis = millis;
            return this;
        }

        /**
         * Sets the maximum timeout.
         * 
         * @param maxTimeout the maximum timeout
         * @return this {@code Builder}
         */
        public Builder setMaxTimeout(final Duration maxTimeout) {
            requireNonNull(maxTimeout, "maxTimeout == null");
            if (maxTimeout.isNegative())
                throw new IllegalArgumentException("maxTimeout < 0");
            final long millis = maxTimeout.toMillis();
            this.maxTimeoutMillis = millis;
            return this;
        }

        /**
         * Sets the base multiplication factor used when calculating the
         * <a href="https://en.wikipedia.org/wiki/Exponential_backoff" target="_blank">exponential backoff</a> delay.
         * 
         * @param multiplier the base multiplier to use when calculating the exponential backoff delay
         * @return this {@code Builder}
         */
        public Builder setMultiplier(final double multiplier) {
            if (multiplier < 1.0)
                throw new IllegalArgumentException("multiplier < 1.0");
            if (!Double.isFinite(multiplier))
                throw new IllegalArgumentException("multiplier is not finite");
            this.multiplier = multiplier;
            return this;
        }

        /**
         * Registers a callback function which will be invoked before waiting to retry {@link ExecutorService#execute(Runnable)
         * execution}, usually used for logging.
         * <p>
         * For example: <pre>
         * {@code builder.onDelay(delay -> ...)}
         * </pre>
         * 
         * @param callback a {@code Consumer} whose {@link Consumer#accept(Object) accept} method will be invoked with the
         *                 current delay {@code Duration}, before waiting to retry execution
         * @return this {@code Builder}
         */
        public Builder onDelay(final Consumer<Duration> callback) {
            requireNonNull(callback, "callback == null");
            this.delay = callback;
            return this;
        }

        /**
         * Registers a callback which will run if the task was accepted by {@link ExecutorService} for execution upon retrying,
         * usually used for logging.
         * 
         * @param success the callback function after the task was accepted
         * @return this {@code Builder}
         */
        public Builder onSuccess(final Runnable success) {
            requireNonNull(success, "success == null");
            this.success = success;
            return this;
        }

        /**
         * Creates a new {@code RetryPolicy} based on the specified criteria.
         * 
         * @return a new {@code RetryPolicy} based on the specified criteria
         */
        public RetryPolicy create() {
            return new RetryPolicy(maxRetries, initialDelayMillis, maxDelayMillis, maxTimeoutMillis, multiplier, delay, success);
        }
    }

    /**
     * Returns a {@link RetryPolicy.Builder} initialized with the specified maximum number of retries.
     * <p>
     * Unless otherwise specified the builder will be {@link Builder#create() configured} with the
     * {@link Builder#setInitialDelay(Duration) initial delay interval} set to {@code 500ms}, no maximum
     * {@link Builder#setMaxDelay(Duration) delay interval} or {@link Builder#setMaxTimeout(Duration) timeout}, and the
     * <a href="https://en.wikipedia.org/wiki/Exponential_backoff" target="_blank">exponential backoff</a>
     * {@link Builder#setMultiplier(double) multiplier} set at {@code 2}.
     * 
     * @param maxRetries the maximum number of retry attempts
     * @return a {@link RetryPolicy.Builder} initialized with the specified maximum number of retries
     */
    public static Builder setMaxRetries(final int maxRetries) {
        return new Builder(maxRetries);
    }

    // The number of total attempts which includes the initial execute (1 more than number of retries)
    private final static ThreadLocal<Integer> attempts = ThreadLocal.withInitial(() -> 0);

    @Override
    public void rejectedExecution(final Runnable r, final ThreadPoolExecutor executor) {
        attempts.set(attempts.get() + 1);

        /*
         * If attempts > 1 we are already in the retry loop so subsequent invocations of rejectedExecution should fallback to
         * the original retry loop.
         */
        if (attempts.get() > 1)
            return;

        long remainingMillis = maxTimeoutMillis;

        try {
            for (int retries = 0;; retries++) {

                /*
                 * The only way retries can equal attempts is if we succeeded
                 */
                if (retries == attempts.get()) {
                    success.run();
                    return;
                }

                if (remainingMillis == 0L)
                    throw new RejectedExecutionException("task " + r.toString() + " rejected from " + executor.toString() + " (maximum timeout (" + maxTimeoutMillis + "ms) exceeded, attempts = " + attempts.get() + ")");

                if (executor.isShutdown())
                    throw new RejectedExecutionException(executor.getClass() + " has been shutdown");

                if (retries == maxRetries)
                    throw new RejectedExecutionException("task " + r.toString() + " rejected from " + executor.toString() + " (attempts = " + attempts.get() + ")");

                final long millis = nextBackOff(retries, remainingMillis);

                remainingMillis -= millis;

                delay.accept(Duration.ofMillis(millis));
                Thread.sleep(millis);

                executor.execute(r);
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RejectedExecutionException("interrupted while waiting to execute task " + r.toString() + " on " + executor.toString(), e);
        } finally {
            attempts.remove();
        }
    }

    private long nextBackOff(final int attempt, final long remainingNanos) {
        final long nanos = (long) (initialDelayMillis * Math.pow(multiplier, attempt));
        final long delay = Math.min(nanos, maxDelayMillis);
        return Math.min(delay, remainingNanos);
    }

}
