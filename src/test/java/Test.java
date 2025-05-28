import java.util.Arrays;
import java.util.Iterator;

import software.leonov.concurrent.PausableThreadPoolExecutor;

public class Test {

    public static void main(String[] args) throws InterruptedException {

        PausableThreadPoolExecutor exec = PausableThreadPoolExecutor.newFixedThreadPool(1).create();

        exec.pause();

        exec.submit(() -> {
            System.out.println("Foo");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.println("Bar");
        });
        
//        exec.submit(() -> {
//            System.out.println("Foo1");
//            try {
//                Thread.sleep(1000);
//            } catch (InterruptedException e) {
//                Thread.currentThread().interrupt();
//            }
//            System.out.println("Bar1");
//        });
        
        
        System.out.println(exec.isPaused());
        
        exec.resume();
        
        exec.shutdown();
        
        exec.pause();
        
        System.out.println(exec.isPaused());
        
        exec.awaitTermination();

//        Thread.setDefaultUncaughtExceptionHandler((t, th) -> {});
//        
//        PausableThreadPool exec = PausableThreadPool.newSingleThreadPool().create();
//        
//        exec.afterExecute((r, t) -> {
//  
//        });
//        
//        exec.execute(new RunnerErr());
//        
//        Thread.sleep(1000);
//        
//        
//        exec.execute(new Runner());
//        
//        exec.shutdown();
//        

    }

    static class RunnerErr implements Runnable {

        @Override
        public void run() {
            throw new RuntimeException();
        }

    }

    static class Runner implements Runnable {

        @Override
        public void run() {
            System.out.println(Thread.currentThread().getName() + ": executing");
        }

    }

}
