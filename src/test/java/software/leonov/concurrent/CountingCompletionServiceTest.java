package software.leonov.concurrent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CountingCompletionServiceTest {
    
    private ExecutorService executor;
    private CountingCompletionService<String> service;
    private List<Callable<String>> tasks;
    
    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(2);
        service = new CountingCompletionService<>(executor);
        tasks = new ArrayList<>();
        
        // Create a few test tasks
        tasks.add(() -> {
            Thread.sleep(100);
            return "Task 1";
        });
        
        tasks.add(() -> {
            Thread.sleep(50);
            return "Task 2";
        });
        
        tasks.add(() -> {
            Thread.sleep(150);
            return "Task 3";
        });
    }
    
    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }
    
    @Test
    void test_state_before_submitting() {
        assertEquals(0, service.getSubmittedCount(), "Initial submitted count should be 0");
        assertEquals(0, service.getRetrievedCount(), "Initial retrieved count should be 0");
        assertFalse(service.hasNext(), "hasNext should be false when no tasks are submitted");
    }
    
    @Test
    void test_submitted_count() {
        service.submit(tasks.get(0));
        assertEquals(1, service.getSubmittedCount(), "Submitted count should increment after submit");
        
        service.submit(tasks.get(1));
        assertEquals(2, service.getSubmittedCount(), "Submitted count should increment after each submit");
    }
    
    @Test
    void test_retrieved_count() throws InterruptedException, ExecutionException {
        service.submit(tasks.get(1)); // Shorter task
        
        // Wait for task to complete and retrieve it
        Future<String> future = service.take();
        String result = future.get();
        
        assertNotNull(result, "Result should not be null");
        assertEquals(1, service.getRetrievedCount(), "Retrieved count should increment after take()");
        assertEquals(1, service.getSubmittedCount(), "Submitted count should remain unchanged");
    }
    
    @Test
    void test_hasNext() throws InterruptedException {
        // Initially no tasks
        assertFalse(service.hasNext(), "hasNext should be false initially");
        
        // Submit tasks
        service.submit(tasks.get(0));
        service.submit(tasks.get(1));
        
        // Now hasNext should return true (tasks submitted but not yet completed)
        assertTrue(service.hasNext(), "hasNext should be true when tasks are submitted");
        
        // Let tasks complete and retrieve them
        Future<String> future1 = service.take();
        assertNotNull(future1, "Future from take() should not be null");
        assertEquals(1, service.getRetrievedCount(), "Retrieved count should be 1 after first take()");
        
        // Should still have one more task
        assertTrue(service.hasNext(), "hasNext should still be true with one task remaining");
        
        Future<String> future2 = service.take();
        assertNotNull(future2, "Second future from take() should not be null");
        assertEquals(2, service.getRetrievedCount(), "Retrieved count should be 2 after second take()");
        
        // All tasks retrieved
        assertFalse(service.hasNext(), "hasNext should be false when all tasks are retrieved");
    }
    
    @Test
    void test_concurrent_submit_and_retrieve() throws InterruptedException {
        // Submit initial task
        service.submit(tasks.get(0));
        
        BooleanLatch latch = new BooleanLatch();
        
        // Start thread that will submit more tasks after a delay
        Thread submitter = new Thread(() -> {
            try {
                latch.await();
                service.submit(tasks.get(1));
                service.submit(tasks.get(2));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        submitter.start();
        
        // Take first task
        Future<String> future1 = service.take();
        assertNotNull(future1, "First future should not be null");
        assertEquals(1, service.getRetrievedCount(), "Retrieved count should be 1");
        
        latch.signal();
        
        // Wait for submitter to add more tasks
        submitter.join();
        assertEquals(3, service.getSubmittedCount(), "Should have 3 submitted tasks total");
        
        // Should be able to retrieve the other tasks
        assertTrue(service.hasNext(), "Should have more tasks after additional submissions");
        
        Future<String> future2 = service.take();
        Future<String> future3 = service.take();
        
        assertNotNull(future2, "Second future should not be null");
        assertNotNull(future3, "Third future should not be null");
        assertEquals(3, service.getRetrievedCount(), "Retrieved count should be 3 at the end");
        assertFalse(service.hasNext(), "hasNext should be false when all tasks are retrieved");
    }
    
    @Test
    @Timeout(value = 1, unit = TimeUnit.SECONDS)
    void test_hasNext_with_no_more_tasks_expected() throws InterruptedException, ExecutionException {
        // Submit and complete all tasks
        service.submit(tasks.get(1)); // Short task
        Future<String> future = service.take();
        future.get(); // Ensure completion
        
        // Counters should reflect this
        assertEquals(1, service.getSubmittedCount(), "Submitted count should be 1");
        assertEquals(1, service.getRetrievedCount(), "Retrieved count should be 1");
        
        // hasNext should return false since all tasks are accounted for
        assertFalse(service.hasNext(), "hasNext should be false when all tasks are retrieved");
    }
    
    @Test
    void test_multiple_threads_retrieving() throws InterruptedException {
        // Submit several tasks
        for (int i = 0; i < 10; i++) {
            final int taskNum = i;
            service.submit(() -> {
                Thread.sleep(50);
                return "Task " + taskNum;
            });
        }
        
        // Create multiple consumer threads
        Thread consumer1 = new Thread(() -> {
            try {
                for (int i = 0; i < 5; i++) {
                    service.take();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        Thread consumer2 = new Thread(() -> {
            try {
                for (int i = 0; i < 5; i++) {
                    service.take();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        consumer1.start();
        consumer2.start();
        
        consumer1.join();
        consumer2.join();
        
        assertEquals(10, service.getSubmittedCount(), "Submitted count should be 10");
        assertEquals(10, service.getRetrievedCount(), "Retrieved count should be 10");
        assertFalse(service.hasNext(), "hasNext should be false after all tasks are retrieved");
    }
}