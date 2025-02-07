import software.leonov.concurrent.PausableThreadPool;

public class Test {

    public static void main(String[] args) throws InterruptedException {
        
        Thread.setDefaultUncaughtExceptionHandler((t, th) -> {});
        
        PausableThreadPool exec = PausableThreadPool.newSingleThreadPool().create();
        
        exec.afterExecute((r, t) -> {
  
        });
        
        exec.execute(new RunnerErr());
        
        Thread.sleep(1000);
        
        
        exec.execute(new Runner());
        
        exec.shutdown();
        
        
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
