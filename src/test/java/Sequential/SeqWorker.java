package Sequential;




import com.haenu.platform.async.callback.ICallback;
import com.haenu.platform.async.callback.ITask;
import com.haenu.platform.async.executor.SystemClock;
import com.haenu.platform.async.task.TaskResult;
import com.haenu.platform.async.wrapper.TaskWrapper;

import java.util.Map;


public class SeqWorker implements ITask<String, String>, ICallback<String, String> {

    @Override
    public String action(String object, Map<String, TaskWrapper> allWrappers) {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return "10001";
    }


    @Override
    public String defaultValue() {
        return "worker0--default";
    }

    @Override
    public void begin() {
        //System.out.println(Thread.currentThread().getName() + "- start --" + System.currentTimeMillis());
    }

    @Override
    public void result(boolean success, String param, TaskResult<String> workResult) {
        if (success) {
            System.out.println("callback worker0 success--" + SystemClock.now() + "----" + workResult.getResult()
                    + "-threadName:" +Thread.currentThread().getName());
        } else {
            System.err.println("callback worker0 failure--" + SystemClock.now() + "----"  + workResult.getResult()
                    + "-threadName:" +Thread.currentThread().getName());
        }
    }

}
