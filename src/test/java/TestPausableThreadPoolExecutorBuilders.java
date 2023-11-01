import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import software.leonov.concurrent.PausableThreadPoolExecutor;

class TestPausableThreadPoolExecutorBuilders {

    @Test
    void test_cached_builder_core_pool_size() {
        assertEquals(0, PausableThreadPoolExecutor.newCachedThreadPool().create().getCorePoolSize());
    }

    @Test
    void test_cached_builder_max_pool_size() {
        assertEquals(Integer.MAX_VALUE, PausableThreadPoolExecutor.newCachedThreadPool().create().getMaximumPoolSize());
    }

    @Test
    void test_cached_builder_queue() {
        assertInstanceOf(SynchronousQueue.class, PausableThreadPoolExecutor.newCachedThreadPool().create().getQueue());
    }

    @Test
    void test_cached_builder_keepAliveTime() {
        assertEquals(60L, PausableThreadPoolExecutor.newCachedThreadPool().create().getKeepAliveTime(TimeUnit.SECONDS));
    }

    @Test
    void test_cached_builder_handler() {
        assertInstanceOf(ThreadPoolExecutor.AbortPolicy.class, PausableThreadPoolExecutor.newCachedThreadPool().create().getRejectedExecutionHandler());
    }

    @Test
    void test_fixed_builder_core_pool_size() {
        assertEquals(Runtime.getRuntime().availableProcessors(), PausableThreadPoolExecutor.newFixedThreadPool().create().getCorePoolSize());
        assertEquals(1, PausableThreadPoolExecutor.newFixedThreadPool(1).create().getCorePoolSize());
    }

    @Test
    void test_fixed_builder_max_pool_size() {
        assertEquals(Runtime.getRuntime().availableProcessors(), PausableThreadPoolExecutor.newFixedThreadPool().create().getMaximumPoolSize());
        assertEquals(1, PausableThreadPoolExecutor.newFixedThreadPool(1).create().getMaximumPoolSize());
    }

    @Test
    void test_fixed_builder_queue() {
        assertInstanceOf(LinkedBlockingQueue.class, PausableThreadPoolExecutor.newFixedThreadPool().create().getQueue());
    }

    @Test
    void test_fixed_builder_keepAliveTime() {
        assertEquals(0L, PausableThreadPoolExecutor.newFixedThreadPool().create().getKeepAliveTime(TimeUnit.SECONDS));
    }

    @Test
    void test_fixed_builder_handler() {
        assertInstanceOf(ThreadPoolExecutor.AbortPolicy.class, PausableThreadPoolExecutor.newFixedThreadPool().create().getRejectedExecutionHandler());
    }

}
