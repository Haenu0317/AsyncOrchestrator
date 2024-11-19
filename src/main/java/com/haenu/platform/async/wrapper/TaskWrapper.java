package com.haenu.platform.async.wrapper;

import com.haenu.platform.async.callback.ICallback;
import com.haenu.platform.async.callback.ITask;
import com.haenu.platform.async.callback.defback.DefaultCallback;
import com.haenu.platform.async.task.DependWrapper;
import com.haenu.platform.async.task.ResultState;
import com.haenu.platform.async.task.TaskResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author haenu
 * @version 1.0
 * @date 2024/11/18 22:09
 */
public class TaskWrapper<T, V> {
    /**
     * 该wrapper的唯一标识
     */
    private String id;

    /**
     * task将来要处理的param
     */
    private T param;

    private ITask<T, V> task;
    private ICallback<T, V> callback;

    private static final int FINISH = 1;
    private static final int ERROR = 2;
    private static final int WORKING = 3;
    private static final int INIT = 0;

    /**
     * 后继任务，可以指定多个。如果为null，则当前任务就是最后一个任务节点。如果只有1个任务，就是串行执行场景，使用当前的线程来执行next任务；
     * 如果有多个任务，就是并行场景，通过多线程处理next任务。
     * -------2
     * 1
     * -------3
     * 如1后面有2、3
     */
    private List<TaskWrapper<?, ?>> nextWrappers;
    /**
     * 依赖的wrappers，有2种情况，1:必须依赖的全部完成后，才能执行自己 2:依赖的任何一个、多个完成了，就可以执行自己
     * 通过must字段来控制是否依赖项必须完成
     */
    private List<DependWrapper> dependWrappers;

    /**
     * 用来标记Task的运行状态，框架内部运行时使用，不对外暴露。这个字段可以保证任务不被重复执行。
     * 标记该事件是否已经被处理过了，譬如已经超时返回false了，后续rpc又收到返回值了，则不再二次回调
     * 0-init, 1-finish, 2-error, 3-tasking
     */
    private AtomicInteger state = new AtomicInteger(0);

    /**
     * 收集所有的wrapper，key是id，以便用于在task工作单元中，获取任意task的执行结果。
     */
    private Map<String, TaskWrapper> forParamUseWrappers;

    /**
     * 存放任务结果，action中的返回值会赋值给它，在result的回调中，可以拿到这个结果。
     * TaskWrapper的运行结果，其中包含了：结果状态标记resultState、Task返回值result、异常信息ex
     * taskResult对框架外部暴露。当前任务action方法可以获取其他task任务的返回值；
     * 当前任务result回调接口可以处理这个返回值；并且如果超时、异常了，可以拿到异常信息。
     */
    private volatile TaskResult<V> taskResult = TaskResult.defaultResult();

    /**
     * 是否在执行自己前，去校验nextWrapper的执行结果<p>
     * 1   4
     * -------3
     * 2
     * 如这种在4执行前，可能3已经执行完毕了（被2执行完后触发的），那么4就没必要执行了。
     * 注意，该属性仅在nextWrapper数量<=1时有效，>1时的情况是不存在的
     */
    private volatile boolean needCheckNextWrapperResult = true;

    TaskWrapper(String id, ITask<T, V> task, T param, ICallback<T, V> callback) {
        if (task == null) {
            throw new NullPointerException("async.task is null");
        }
        this.task = task;
        this.param = param;
        this.id = id;
        //允许不设置回调
        this.callback = callback == null ? new DefaultCallback<>() : callback;
    }

    public TaskResult<V> getTaskResult() {
        return taskResult;
    }

    public List<TaskWrapper<?, ?>> getNextWrappers() {
        return nextWrappers;
    }

    public void setParam(T param) {
        this.param = param;
    }

    boolean checkIsNullResult() {
        return ResultState.DEFAULT == taskResult.getResultState();
    }

    public String getId() {
        return id;
    }

    private int getState() {
        return state.get();
    }

    private boolean compareAndSetState(int expect, int update) {
        return this.state.compareAndSet(expect, update);
    }


    void setNeedCheckNextWrapperResult(boolean needCheckNextWrapperResult) {
        this.needCheckNextWrapperResult = needCheckNextWrapperResult;
    }

    protected void addDepend(TaskWrapper<?, ?> taskWrapper, boolean must) {
        addDepend(new DependWrapper(taskWrapper, must));
    }

    private void addDependWrappers(List<DependWrapper> dependWrappers) {
        if (dependWrappers == null) {
            return;
        }
        for (DependWrapper wrapper : dependWrappers) {
            addDepend(wrapper);
        }
    }

    protected void addDepend(DependWrapper dependWrapper) {
        if (dependWrappers == null) {
            dependWrappers = new ArrayList<>();
        }
        //如果依赖的是重复的同一个，就不重复添加了
        for (DependWrapper wrapper : dependWrappers) {
            if (wrapper.equals(dependWrapper)) {
                return;
            }
        }
        dependWrappers.add(dependWrapper);
    }

    private void addNextWrappers(List<TaskWrapper<?, ?>> wrappers) {
        if (wrappers == null) {
            return;
        }
        for (TaskWrapper<?, ?> wrapper : wrappers) {
            addNext(wrapper);
        }
    }

    protected void addNext(TaskWrapper<?, ?> taskWrapper) {
        if (nextWrappers == null) {
            nextWrappers = new ArrayList<>();
        }
        //避免添加重复
        for (TaskWrapper wrapper : nextWrappers) {
            if (taskWrapper.equals(wrapper)) {
                return;
            }
        }
        nextWrappers.add(taskWrapper);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TaskWrapper<?, ?> that = (TaskWrapper<?, ?>) o;
        return needCheckNextWrapperResult == that.needCheckNextWrapperResult &&
                Objects.equals(param, that.param) &&
                Objects.equals(task, that.task) &&
                Objects.equals(callback, that.callback) &&
                Objects.equals(nextWrappers, that.nextWrappers) &&
                Objects.equals(dependWrappers, that.dependWrappers) &&
                Objects.equals(state, that.state) &&
                Objects.equals(taskResult, that.taskResult);
    }

    @Override
    public int hashCode() {
        return Objects.hash(param, task, callback, nextWrappers, dependWrappers, state, taskResult, needCheckNextWrapperResult);
    }

    private TaskResult<V> defaultResult() {
        taskResult.setResultState(ResultState.TIMEOUT);
        taskResult.setResult(task.defaultValue());
        return taskResult;
    }

    private TaskResult<V> defaultExResult(Exception ex) {
        taskResult.setResultState(ResultState.EXCEPTION);
        taskResult.setResult(task.defaultValue());
        taskResult.setEx(ex);
        return taskResult;
    }

}
