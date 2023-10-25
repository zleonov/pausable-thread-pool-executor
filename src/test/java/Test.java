import software.leonov.concurrent.PausableThreadPoolExecutor;

public class Test {

    public static void main(String[] args) {
        PausableThreadPoolExecutor exec = PausableThreadPoolExecutor.fixedThreadPool().create();
        
        exec.shutdown();
        
        
        System.out.println(exec.isTerminated());
        System.out.println(exec.isShutdown());
        System.out.println(exec.pause());
    }

}
