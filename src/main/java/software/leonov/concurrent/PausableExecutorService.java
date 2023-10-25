package software.leonov.concurrent;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * An {@code ExecutorService} which can pause and resume the processing of pending tasks.
 * 
 * @author Zhenya Leonov
 */
public interface PausableExecutorService extends ExecutorService {

    /**
     * Returns {@code true} if the {@code ExecutorService} is paused.
     * 
     * @return {@code true} if the {@code ExecutorService} is paused
     */
    public boolean isPaused();

    /**
     * Attempts to pause the processing of pending tasks.
     * <p>
     * Calling this method is a no-op if the {@code ExecutorService} is already paused.
     * 
     * @return {@code true} if successful or {@code false} if the {@code ExecutorService} cannot be paused
     */
    public boolean pause();

    /**
     * Resumes to processing of pending tasks.
     * <p>
     * Calling this method is a no-op if the {@code ExecutorService} is not paused.
     */
    public void resume();

    /**
     * Waits for the {@code ExecutorService} to terminate after a shutdown request.
     * <p>
     * <b>Warning:</b> This method will block forever until the {@code ExecutorService} terminates. If active tasks are
     * deadlocked this thread must be interrupted.
     * 
     * @throws InterruptedException if interrupted while waiting
     */
    default void ensureTermination() throws InterruptedException {
        awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }

    /**
     * Stops the processing of pending tasks but does not attempt to stop actively executing tasks. All pending tasks are
     * drained (removed) from the task queue and returned when this method completes.
     * <p>
     * This method does not wait for actively executing tasks to terminate. Use {@link #awaitTermination awaitTermination}
     * to do that.
     * <p>
     * This method is the middle ground between {@link #shutdown()} and {@link #shutdownNow()}:
     * <ul>
     * <li>{@link #shutdown()}: all actively executing tasks and pending tasks are allowed to continue, but no new tasks
     * will be accepted</li>
     * <li><b>shutdownFast()</b>: all actively executing tasks are allowed to continue, pending tasks are removed, and no
     * new tasks will be accepted</li>
     * <li>{@link #shutdownNow()}: all actively executing tasks are <u>interrupted</u>, pending tasks are removed, and no
     * new tasks will be accepted</li>
     * </ul>
     * 
     * @return the pending tasks that never commenced execution
     */
    public List<Runnable> shutdownFast();

}