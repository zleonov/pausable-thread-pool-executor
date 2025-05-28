import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import com.google.common.util.concurrent.MoreExecutors;

import software.leonov.concurrent.Execution;

public class TestSystemExitShutdownHook {

    public static void main(String[] args) {
        final ExecutorService exec = Executors.newSingleThreadExecutor();
        
        exec.execute(()->{
            IntStream.range(1, 11).forEach(i -> {
                System.out.println(i);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                
            });
        });
        
       
        MoreExecutors.addDelayedShutdownHook(exec, 5, TimeUnit.SECONDS);
        //Execution.shutdownAndAwaitTerminationOnSystemExit(exec, Duration.ofSeconds(5));
        //System.exit(0);

    }

}
