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
        assertEquals(0, PausableThreadPoolExecutor.cachedThreadPool().create().getCorePoolSize());
    }

    @Test
    void test_cached_builder_max_pool_size() {
        assertEquals(Integer.MAX_VALUE, PausableThreadPoolExecutor.cachedThreadPool().create().getMaximumPoolSize());
    }

    @Test
    void test_cached_builder_queue() {
        assertInstanceOf(SynchronousQueue.class, PausableThreadPoolExecutor.cachedThreadPool().create().getQueue());
    }

    @Test
    void test_cached_builder_keepAliveTime() {
        assertEquals(60L, PausableThreadPoolExecutor.cachedThreadPool().create().getKeepAliveTime(TimeUnit.SECONDS));
    }

    @Test
    void test_cached_builder_handler() {
        assertInstanceOf(ThreadPoolExecutor.AbortPolicy.class, PausableThreadPoolExecutor.cachedThreadPool().create().getRejectedExecutionHandler());
    }

    @Test
    void test_fixed_builder_core_pool_size() {
        assertEquals(Runtime.getRuntime().availableProcessors(), PausableThreadPoolExecutor.fixedThreadPool().create().getCorePoolSize());
        assertEquals(1, PausableThreadPoolExecutor.fixedThreadPool(1).create().getCorePoolSize());
    }

    @Test
    void test_fixed_builder_max_pool_size() {
        assertEquals(Runtime.getRuntime().availableProcessors(), PausableThreadPoolExecutor.fixedThreadPool().create().getMaximumPoolSize());
        assertEquals(1, PausableThreadPoolExecutor.fixedThreadPool(1).create().getMaximumPoolSize());
    }

    @Test
    void test_fixed_builder_queue() {
        assertInstanceOf(LinkedBlockingQueue.class, PausableThreadPoolExecutor.fixedThreadPool().create().getQueue());
    }

    @Test
    void test_fixed_builder_keepAliveTime() {
        assertEquals(0L, PausableThreadPoolExecutor.fixedThreadPool().create().getKeepAliveTime(TimeUnit.SECONDS));
    }

    @Test
    void test_fixed_builder_handler() {
        assertInstanceOf(ThreadPoolExecutor.AbortPolicy.class, PausableThreadPoolExecutor.fixedThreadPool().create().getRejectedExecutionHandler());
    }

}
