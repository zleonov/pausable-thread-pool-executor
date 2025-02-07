package software.leonov.concurrent;

import static java.util.Objects.requireNonNull;
import static software.leonov.concurrent.PausableThreadPoolExecutor.DO_NOTHING_CONSUMER;
import static software.leonov.concurrent.PausableThreadPoolExecutor.DO_NOTHING_RUNNABLE;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Consumer;

/**
 * A handler for rejected tasks that attempts to retry {@link ExecutorService#execute(Runnable) execution} (on the
 * calling thread), unless the executor has been {@link ExecutorService#isShutdown() shutdown}, in which case it throws
 * an {@link RejectedExecutionException}.
 * <p>
 * This policy can be configured to use either a {@link Builder#setInitialDelay(Duration) constant} or an
 * <a href="https://en.wikipedia.org/wiki/Exponential_backoff" target="_blank">exponential backoff</a> delay
 * {@link Builder#setMultiplier(double) strategy} between retries. Instances of this policy are created using a
 * {@link RetryPolicy.Builder builder} obtained from {@link #setMaxRetries(int)}.
 * <p>
 * For example: <pre>{@code
 *   RetryPolicy.setMaxRetries(...)
 *              .setInitialDelay(Duration.ofMillis(...))
 *              .setMaxDelay(Duration.ofMinutes(...))
 *              .setMultiplier(...)
 *              .onRetry(millis -> ...)
 *              .onSuccess(()   -> ...)
 *              .create();}</pre>
 * <p>
 * <b>Discussion:</b></br>
 * Java provides 4 <i>saturation policies</i> to handle rejected tasks:
 * {@link java.util.concurrent.ThreadPoolExecutor.AbortPolicy AbortPolicy} (default),
 * {@link java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy CallerRunsPolicy},
 * {@link java.util.concurrent.ThreadPoolExecutor.DiscardPolicy DiscardPolicy}, and
 * {@link java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy DiscardOldestPolicy} when using a
 * {@code ThreadPoolExecutor} with a bounded {@link ThreadPoolExecutor#getQueue() work queue}. There is no predefined
 * saturation policy to make the caller block when the queue is full. {@code CallerRunsPolicy} provides the closest
 * functionality, executing the rejected task in the thread which tried to submit the task, de facto throttling task
 * submission.
 * <p>
 * {@code RetryPolicy} offers advantages over {@code CallerRunsPolicy} when the average wait time for task acceptance is
 * shorter than the task execution time. For example, in a system where a primary dispatcher thread continuously
 * delegates a mix of short and long-running tasks to a large executor pool, retrying instead of executing a task
 * directly allows the dispatcher thread to resume other operations sooner (on average), enhancing overall system
 * throughput and responsiveness.
 * </p>
 * <p>
 * {@code RetryPolicy} also promotes a conceptually <i>cleaner</i> design by ensuring that all tasks execute
 * <i>within</i> the thread pool. For example, this can simplify troubleshooting, as it avoids scenarios where the main
 * dispatcher thread suddenly begins executing tasks directly, making it harder to trace the execution flow in debug
 * logs.
 * 
 * @author Zhenya Leonov
 */
public class RetryPolicy implements RejectedExecutionHandler {

    private final int maxRetries;
    private final long initialDelayMillis;
    private final long maxDelayMillis;
    private final double multiplier;

    private final Consumer<Long> retry;
    private final Runnable success;

    private RetryPolicy(final int maxRetries, final long initialDelayMillis, final long maxDelayMillis, final double multiplier, final Consumer<Long> waiting, final Runnable success) {
        this.maxRetries = maxRetries;
        this.initialDelayMillis = initialDelayMillis;
        this.maxDelayMillis = maxDelayMillis;
        this.multiplier = multiplier;
        this.retry = waiting;
        this.success = success;
    }

    /**
     * A builder of {@code RetryPolicy} instances.
     * <p>
     * Unless otherwise specified this builder will be {@link Builder#create() configured} with the
     * {@link Builder#setInitialDelay(Duration) initial delay interval} set to {@code 500ms}, no
     * {@link Builder#setMaxDelay(Duration) maximum delay interval}, and the
     * <a href="https://en.wikipedia.org/wiki/Exponential_backoff" target="_blank">exponential backoff</a>
     * {@link Builder#setMultiplier(double) multiplier} set at {@code 2}.
     */
    public static class Builder {
        private final int maxRetries;
        private long initialDelayMillis = 500;
        private long maxDelayMillis = Long.MAX_VALUE;
        private double multiplier = 2;

        private Runnable success = DO_NOTHING_RUNNABLE;
        @SuppressWarnings("unchecked")
        private Consumer<Long> retry = (Consumer<Long>) DO_NOTHING_CONSUMER;

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
         * Registers a callback function which will be invoked before waiting retrying {@link ExecutorService#execute(Runnable)
         * execution}, usually used for logging.
         * <p>
         * For example: <pre>
         * {@code builder.onRetry(millis -> ...)}
         * </pre>
         * 
         * @param callback a {@code Consumer} whose {@link Consumer#accept(Object) accept} method will be invoked with the
         *                 current delay (in milliseconds) before retrying execution
         * @return this {@code Builder}
         */
        public Builder onRetry(final Consumer<Long> callback) {
            requireNonNull(callback, "callback == null");
            this.retry = callback;
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
            return new RetryPolicy(maxRetries, initialDelayMillis, maxDelayMillis, multiplier, retry, success);
        }
    }

    /**
     * Returns a {@link RetryPolicy.Builder} initialized with the specified maximum number of retries.
     * <p>
     * Unless otherwise specified this builder will be {@link Builder#create() configured} with the
     * {@link Builder#setInitialDelay(Duration) initial delay interval} set to {@code 500ms}, no
     * {@link Builder#setMaxDelay(Duration) maximum delay interval}, and the
     * <a href="https://en.wikipedia.org/wiki/Exponential_backoff" target="_blank">exponential backoff</a>
     * {@link Builder#setMultiplier(double) multiplier} set at {@code 2}.
     * 
     * @param maxRetries the maximum number of retry attempts
     * @return a {@link RetryPolicy.Builder} initialized with the specified maximum number of retries
     */
    public static Builder setMaxRetries(final int maxRetries) {
        return new Builder(maxRetries);
    }

    // the number of total attempts which includes the initial execute (1 more than number of retries)
    private final static ThreadLocal<Integer> attempts = ThreadLocal.withInitial(() -> 0);

    @Override
    public void rejectedExecution(final Runnable r, final ThreadPoolExecutor executor) {
        attempts.set(attempts.get() + 1);

        /*
         * If attempts > 1 we are already in the retry loop so subsequent invocations of rejectedExecution should go back to the
         * retry loop.
         */
        if (attempts.get() > 1)
            return;

        try {
            for (int retries = 0;; retries++) {

                /*
                 * The only way retries can equal attempts is if we succeeded
                 */
                if (retries == attempts.get()) {
                    success.run();
                    return;
                }

                if (executor.isShutdown())
                    throw new RejectedExecutionException(executor.getClass() + " has been shutdown");

                if (retries == maxRetries)
                    throw new RejectedExecutionException("task " + r.toString() + " rejected from " + executor.toString() + " (attempts = " + attempts.get() + ")");

                final long millis = nextBackOff(retries);

                retry.accept(millis);
                Thread.sleep(millis);

                executor.execute(r);
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RejectedExecutionException("interrupted while waiting to execute task " + r.toString() + " on " + executor.toString());
        } finally {
            attempts.remove();
        }
    }

    private long nextBackOff(final int attempt) {
        final long delay = (long) (initialDelayMillis * Math.pow(multiplier, attempt));
        return Math.min(delay, maxDelayMillis);
    }

}
