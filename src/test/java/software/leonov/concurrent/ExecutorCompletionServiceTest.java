package software.leonov.concurrent;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static software.leonov.concurrent.Execution.shutdownAndAwaitTermination;

import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;

class ExecutorCompletionServiceTest {

    /**
     * Creating a new ECS with null Executor throw NPE
     */
    @Test
    public void test_constructor_NPE() {
        assertThrows(NullPointerException.class, () -> new CountingCompletionService<>(null));
        assertThrows(NullPointerException.class, () -> new CountingCompletionService<>(Executors.newCachedThreadPool(), null));
    }

    @Test
    public void test_submit_NPE() throws InterruptedException {
        final ExecutorService                 e   = Executors.newCachedThreadPool();
        final CountingCompletionService<Boolean> ecs = new CountingCompletionService<>(e);
        assertThrows(NullPointerException.class, () -> ecs.submit(null));
        assertThrows(NullPointerException.class, () -> ecs.submit(null, Boolean.TRUE));
        shutdownAndAwaitTermination(e);
    }

//    /**
//     * A taken submitted task is completed
//     */ 
//    public void testTake() {
//        ExecutorService e = Executors.newCachedThreadPool();
//        ExecutorCompletionService ecs = new ExecutorCompletionService(e);
//        try {
//            Callable c = new StringTask();
//            ecs.submit(c);
//            Future f = ecs.take();
//            assertTrue(f.isDone());
//        } catch (Exception ex) {
//            unexpectedException();
//        } finally {
//            joinPool(e);
//        }
//    }
//
//    /**
//     * Take returns the same future object returned by submit
//     */ 
//    public void testTake2() {
//        ExecutorService e = Executors.newCachedThreadPool();
//        ExecutorCompletionService ecs = new ExecutorCompletionService(e);
//        try {
//            Callable c = new StringTask();
//            Future f1 = ecs.submit(c);
//            Future f2 = ecs.take();
//            assertSame(f1, f2);
//        } catch (Exception ex) {
//            unexpectedException();
//        } finally {
//            joinPool(e);
//        }
//    }
//
//    /**
//     * If poll returns non-null, the returned task is completed
//     */ 
//    public void testPoll1() {
//        ExecutorService e = Executors.newCachedThreadPool();
//        ExecutorCompletionService ecs = new ExecutorCompletionService(e);
//        try {
//            assertNull(ecs.poll());
//            Callable c = new StringTask();
//            ecs.submit(c);
//            Thread.sleep(SHORT_DELAY_MS);
//            for (;;) {
//                Future f = ecs.poll();
//                if (f != null) {
//                    assertTrue(f.isDone());
//                    break;
//                }
//            }
//        } catch (Exception ex) {
//            unexpectedException();
//        } finally {
//            joinPool(e);
//        }
//    }
//
//    /**
//     * If timed poll returns non-null, the returned task is completed
//     */ 
//    public void testPoll2() {
//        ExecutorService e = Executors.newCachedThreadPool();
//        ExecutorCompletionService ecs = new ExecutorCompletionService(e);
//        try {
//            assertNull(ecs.poll());
//            Callable c = new StringTask();
//            ecs.submit(c);
//            Future f = ecs.poll(SHORT_DELAY_MS, TimeUnit.MILLISECONDS);
//            if (f != null) 
//                assertTrue(f.isDone());
//        } catch (Exception ex) {
//            unexpectedException();
//        } finally {
//            joinPool(e);
//        }
//    }
}