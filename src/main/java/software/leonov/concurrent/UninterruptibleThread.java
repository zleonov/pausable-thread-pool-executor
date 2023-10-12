package software.leonov.concurrent;

import static java.util.Objects.requireNonNull;

import java.util.concurrent.Callable;

/**
 * A {@code Thread} that offers users precise control over when and under what conditions it can be interrupted. When an
 * attempt is made to {@link Thread#interrupt() interrupt} this thread while it's in a non-interruptible state, the
 * interrupt request is deferred until the thread's state changes or the target {@code Runnable} task is completed.
 * <p>
 * This class is not designed for extension; therefore, the sole means of instantiation is by supplying the target
 * runnable via one of its constructors.
 * <p>
 * For example, the following code will print {@code 0123456789} despite attempts to interrupt the thread:
 * 
 * <pre>
 * final UninterruptibleThread thread = new UninterruptibleThread(() -> {
 *     runUninterruptibly(() -> {
 *         for (int i = 0; i < 10; i++) {
 *             System.out.print(i);
 *             TimeUnit.SECONDS.sleep(1);
 *         }
 *     });
 * });
 * 
 * thread.start();
 * TimeUnit.SECONDS.timedJoin(thread, 1);
 * thread.interrupt();
 * </pre>
 * 
 * When the {@link #runUninterruptibly(InterruptibleRunnable) runUninterruptibly} method returns the thread's
 * interrupted flag will be set to {@code true}.
 * 
 * @author Zhenya Leonov
 */
public final class UninterruptibleThread extends Thread {

    private boolean interrupted = false;
    private boolean interruptable = true;

    /**
     * See {@link Thread#Thread(Runnable, String) new Thread(Runnable, String)}.
     *
     * @param runnable the {@code Runnable} whose {@code run} method is invoked when this thread is started
     */
    public UninterruptibleThread(final Runnable runnable) {
        super(requireNonNull(runnable, "runnable == null"));
    }

    /**
     * See {@link Thread#Thread(Runnable, String) new Thread(Runnable, String)}.
     *
     * @param runnable the {@code Runnable} whose {@code run} method is invoked when this thread is started or {@code null}
     * @param name     the name of the new thread
     */
    public UninterruptibleThread(final Runnable runnable, final String name) {
        super(requireNonNull(runnable, "runnable == null"), name);
    }

    /**
     * See {@link Thread#Thread(ThreadGroup, Runnable, String) new Thread(ThreadGroup, Runnable, String)}.
     *
     * @param group    the specified {@code ThreadGroup}
     * @param runnable the {@code Runnable} whose {@code run} method is invoked when this thread is started or {@code null}
     * @param name     the thread name
     * @throws SecurityException if the current thread cannot create a thread in the specified thread group or cannot
     *                           override the context class loader methods.
     */
    public UninterruptibleThread(final ThreadGroup group, final Runnable runnable, final String name) {
        super(group, requireNonNull(runnable, "runnable == null"), name);
    }

    /**
     * See {@link Thread#Thread(ThreadGroup, Runnable, String) new Thread(ThreadGroup, Runnable, String)}.
     *
     * @param group     the specified {@code ThreadGroup}
     * @param runnable  the {@code Runnable} whose {@code run} method is invoked when this thread is started or {@code null}
     * @param name      the thread name
     * @param stackSize the desired stack size for the new thread, or zero to indicate that this parameter is to be ignored
     * @throws SecurityException if the current thread cannot create a thread in the specified thread group
     */
    public UninterruptibleThread(final ThreadGroup group, final Runnable runnable, final String name, final long stackSize) {
        super(group, requireNonNull(runnable, "runnable == null"), name, stackSize);
    }

    /**
     * Attempts to interrupt this thread. If the thread is in a non-interruptible state, the interrupt request is deferred
     * until the thread's state changes or the target {@code Runnable} task is completed.
     */
    @Override
    public void interrupt() {
        if (interruptable)
            super.interrupt();
        else
            interrupted = true;
    }

    /**
     * Enables the current thread's interruption.
     */
    public static void enableInterruption() {
        final UninterruptibleThread t = currentThread();
        t.interruptable = true;
        if (t.interrupted)
            t.interrupt();
    }

    /**
     * Disables the current thread's interruption ability. If the thread is interrupted in this state, the request will be
     * deferred until the thread's interruption ability is {@link #enableInterruption() enabled}.
     */
    public static void disableInterruption() {
        final UninterruptibleThread t = currentThread();
        t.interruptable = false;
        t.interrupted = false;
    }

    /**
     * Runs the specified runnable uninterruptibly within an {@code UninterruptibleThread}. Any attempt to interrupt the
     * thread will be deferred until the runnable is complete.
     * <p>
     * <b>Warning:</b> Invoking this method within a standard {@code Thread} will result in an {@code AssertionError}.
     * 
     * @param runnable the specified runnable
     */
    public static void runUninterruptibly(final InterruptibleRunnable runnable) {
        requireNonNull(runnable, "runnable == null");

        disableInterruption();
        try {
            ((Runnable) () -> {
                try {
                    runnable.run();
                } catch (final InterruptedException e) { // cannot happen
                }
            }).run();
        } finally {
            enableInterruption();
        }
    }

    /**
     * Executes the specified callable operation uninterruptibly within an {@code UninterruptibleThread}. Any attempt to
     * interrupt the thread will be deferred until the callable operation is complete.
     * <p>
     * <b>Warning:</b> Invoking this method within a standard {@code Thread} will result in an {@code AssertionError}.
     * 
     * @param <T>      the type of object the callable returns
     * @param callable the specified callable
     * @return the result of the computation
     * @throws Exception if an error occurs
     */
    public static <T> T runUninterruptibly(final Callable<T> callable) throws Exception {
        requireNonNull(callable, "callable == null");

        disableInterruption();
        try {
            return callable.call();
        } finally {
            enableInterruption();
        }
    }

    /**
     * Returns a reference to the currently executing {@link UninterruptibleThread}.
     * 
     * @return a reference to the currently executing {@link UninterruptibleThread}
     */
    public static UninterruptibleThread currentThread() {
        final Thread t = Thread.currentThread();

        if (t instanceof UninterruptibleThread)
            return (UninterruptibleThread) t;
        else
            throw new AssertionError("current thread is not an instance of " + UninterruptibleThread.class.getSimpleName());
    }

    /**
     * Mirror of the {@link Runnable} interface whose {@code run()} method can throw an {@code InterruptedException}.
     */
    @FunctionalInterface
    public interface InterruptibleRunnable {

        /**
         * The general contract of the method {@code run()} is that it may take any action whatsoever.
         * 
         * @throws InterruptedException if the {@code run} method responds {@code Thread} {@link Thread#interrupt()
         *                              interruption} with an {@code InterruptedException}
         */
        public void run() throws InterruptedException;
    }

}