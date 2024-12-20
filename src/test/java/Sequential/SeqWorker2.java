package Sequential;


import com.haenu.platform.async.callback.ICallback;
import com.haenu.platform.async.callback.ITask;
import com.haenu.platform.async.executor.SystemClock;
import com.haenu.platform.async.task.TaskResult;
import com.haenu.platform.async.wrapper.TaskWrapper;


import java.util.Map;

/**
 * @author wuweifeng wrote on 2019-11-20.
 */
public class SeqWorker2 implements ITask<String, String>, ICallback<String, String> {

    @Override
    public String action(String object, Map<String, TaskWrapper> allWrappers) {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return "result = " + SystemClock.now() + "---param = " + object + " from 0";
    }

    @Override
    public String defaultValue() {
        return "worker2--default";
    }

    @Override
    public void begin() {
        //System.out.println(Thread.currentThread().getName() + "- start --" + System.currentTimeMillis());
    }

    @Override
    public void result(boolean success, String param, TaskResult<String> workResult) {
        if (success) {
            System.out.println("callback worker2 success--" + SystemClock.now() + "----" + workResult.getResult()
                    + "-threadName:" +Thread.currentThread().getName());
        } else {
            System.err.println("callback worker2 failure--" + SystemClock.now() + "----"  + workResult.getResult()
                    + "-threadName:" +Thread.currentThread().getName());
        }
    }

}
