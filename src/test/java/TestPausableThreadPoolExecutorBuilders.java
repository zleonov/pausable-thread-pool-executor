import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import software.leonov.concurrent.PausableThreadPool;

class TestPausableThreadPoolExecutorBuilders {

    @Test
    void test_cached_builder_core_pool_size() {
        assertEquals(0, PausableThreadPool.newCachedThreadPool().create().getCorePoolSize());
    }

    @Test
    void test_cached_builder_max_pool_size() {
        assertEquals(Integer.MAX_VALUE, PausableThreadPool.newCachedThreadPool().create().getMaximumPoolSize());
    }

    @Test
    void test_cached_builder_queue() {
        assertInstanceOf(SynchronousQueue.class, PausableThreadPool.newCachedThreadPool().create().getQueue());
    }

    @Test
    void test_cached_builder_keepAliveTime() {
        assertEquals(60L, PausableThreadPool.newCachedThreadPool().create().getKeepAliveTime(TimeUnit.SECONDS));
    }

    @Test
    void test_cached_builder_handler() {
        assertInstanceOf(ThreadPoolExecutor.AbortPolicy.class, PausableThreadPool.newCachedThreadPool().create().getRejectedExecutionHandler());
    }

    @Test
    void test_fixed_builder_core_pool_size() {
        assertEquals(Runtime.getRuntime().availableProcessors() - 1, PausableThreadPool.newFixedThreadPool().create().getCorePoolSize());
        assertEquals(1, PausableThreadPool.newFixedThreadPool(1).create().getCorePoolSize());
    }

    @Test
    void test_fixed_builder_max_pool_size() {
        assertEquals(Runtime.getRuntime().availableProcessors() - 1, PausableThreadPool.newFixedThreadPool().create().getMaximumPoolSize());
        assertEquals(1, PausableThreadPool.newFixedThreadPool(1).create().getMaximumPoolSize());
    }

    @Test
    void test_fixed_builder_queue() {
        assertInstanceOf(LinkedBlockingQueue.class, PausableThreadPool.newFixedThreadPool().create().getQueue());
    }

    @Test
    void test_fixed_builder_keepAliveTime() {
        assertEquals(0L, PausableThreadPool.newFixedThreadPool().create().getKeepAliveTime(TimeUnit.SECONDS));
    }

    @Test
    void test_fixed_builder_handler() {
        assertInstanceOf(ThreadPoolExecutor.AbortPolicy.class, PausableThreadPool.newFixedThreadPool().create().getRejectedExecutionHandler());
    }

}
