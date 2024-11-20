package Sequential;

import com.haenu.platform.async.executor.Async;
import com.haenu.platform.async.executor.SystemClock;
import com.haenu.platform.async.wrapper.TaskWrapper;

import java.util.concurrent.ExecutionException;

/**
 * 串行测试
 * @author haenu
 * @version 1.0
 */
public class TestSequential {
    public static void main(String[] args) throws InterruptedException, ExecutionException {


        SeqWorker w = new SeqWorker();
        SeqWorker1 w1 = new SeqWorker1();
        SeqWorker2 w2 = new SeqWorker2();

        //顺序0-1-2
        TaskWrapper<String, String> workerWrapper2 =  new TaskWrapper.Builder<String, String>()
                .task(w2)
                .callback(w2)
                .param("2")
                .build();

        TaskWrapper<String, String> workerWrapper1 =  new TaskWrapper.Builder<String, String>()
                .task(w1)
                .callback(w1)
                .param("1")
                .next(workerWrapper2)
                .build();

        TaskWrapper<String, String> workerWrapper =  new TaskWrapper.Builder<String, String>()
                .id("work1")
                .task(w)
                .callback(w)
                .param("0")
                .next(workerWrapper1)
                .build();

        testNormal(workerWrapper);

        //testGroupTimeout(workerWrapper);
    }

    private static void testNormal(TaskWrapper<String, String> workerWrapper) throws ExecutionException, InterruptedException {
        long now = SystemClock.now();
        System.out.println("begin-" + now);

        Async.beginTask(350000000, workerWrapper);

        System.out.println("end-" + SystemClock.now());
        System.err.println("cost-" + (SystemClock.now() - now));

        Async.shutDown();
    }

    private static void testGroupTimeout(TaskWrapper<String, String> workerWrapper) throws ExecutionException, InterruptedException {
        long now = SystemClock.now();
        System.out.println("begin-" + now);

        Async.beginTask(2500, workerWrapper);

        System.out.println("end-" + SystemClock.now());
        System.err.println("cost-" + (SystemClock.now() - now));

        Async.shutDown();
    }
}
