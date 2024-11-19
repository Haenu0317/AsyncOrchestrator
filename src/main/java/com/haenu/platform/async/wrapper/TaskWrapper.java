package com.haenu.platform.async.wrapper;

import com.haenu.platform.async.callback.ICallback;
import com.haenu.platform.async.callback.ITask;
import com.haenu.platform.async.callback.defback.DefaultCallback;
import com.haenu.platform.async.exception.SkippedException;
import com.haenu.platform.async.executor.SystemClock;
import com.haenu.platform.async.task.DependWrapper;
import com.haenu.platform.async.task.ResultState;
import com.haenu.platform.async.task.TaskResult;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
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

    public void task(ExecutorService executorService, long remainTime, Map<String, TaskWrapper> forParamUseWrappers) {
        task(executorService, null, remainTime, forParamUseWrappers);
    }

    /**
     * 开始工作
     * executorService：自定义线程池，不自定义的话，就走默认的COMMON_POOL。默认的线程池是不定长线程池。
     * fromWrapper：本次task是由哪个上游TaskWrapper发起的。
     * remainTime：剩余的时间，用来监控任务超时的。随着一组任务的执行，这个值从全局设置的timeout时间逐渐减少，当remainTime<=0时，任务就超时了。
     * forParamUseWrappers：缓存一组任务所有的TaskWrapper。key：id，value：TaskWrapper引用。
     * 流程图: https://img.haenu.cn/img/20241119152305.png
     * <p>
     * 1.缓存所有TaskWrapper
     * 2.任务超时处理
     * 3.Check是否执行过了，避免重复处理
     * 4.Check 后继next是否已经开始执行了，避免多余的处理
     * 5.没有依赖Wrapper情况处理，则当前任务就是起始节点。
     * 6.有依赖Wrapper情况处理，又区分只有1个依赖任务，或者有多个依赖任务的处理。
     */
    private void task(ExecutorService executorService, TaskWrapper fromWrapper, long remainTime, Map<String, TaskWrapper> forParamUseWrappers) {
        //引用指向
        this.forParamUseWrappers = forParamUseWrappers;

        //1.收集所有的wrapper，key是id，以便用于在Task工作单元中，获取任意Task的执行结果。
        forParamUseWrappers.put(id, this);

        //时钟类获取当前时间
        long now = SystemClock.now();

        //2.总的已经超时了，就快速失败，进行下一个
        if (remainTime <= 0) {
            fastFail(INIT, null);
            beginNext(executorService, now, remainTime);
            return;
        }

        //3.如果自己已经执行过了，继续处理下一个任务
        //可能有多个依赖，其中的一个依赖已经执行完了，并且自己也已开始执行或执行完毕。当另一个依赖执行完毕，又进来该方法时，就不重复处理了
        if (getState() == FINISH || getState() == ERROR) {
            beginNext(executorService, now, remainTime);
            return;
        }

        //4.如果在执行前需要校验nextWrapper的状态，仅在nextWrappers <= 1时有效
        if (needCheckNextWrapperResult) {
            //如果自己的next链上有已经出结果或已经开始执行的任务了，自己就不用继续了，SKIP跳过任务，不执行。
            if (!checkNextWrapperResult()) {
                //FastFail SKIP，new SkippedException()
                fastFail(INIT, new SkippedException());
                beginNext(executorService, now, remainTime);
                return;
            }
        }

        //5.如果没有任何依赖，说明自己就是第一批要执行的
        if (dependWrappers == null || dependWrappers.size() == 0) {
            //5.1 执行当前任务
            fire();
            //5.2 开始后继任务
            beginNext(executorService, now, remainTime);
            return;
        }

        /*如果有前方依赖，存在两种情况
         一种是前面只有一个wrapper。即 A  ->  B
        一种是前面有多个wrapper。A C D ->   B。需要A、C、D都完成了才能轮到B。但是无论是A执行完，还是C执行完，都会去唤醒B。
        所以需要B来做判断，必须A、C、D都完成，自己才能执行 */

        /**
         * 只有1个依赖任务时，只要依赖任务正常结束了，就可以执行自己、以及后继任务了。
         * 有多个依赖任务时，需要判断must的依赖任务是否执行完了，如果执行完了才能执行自己。
         * 这里需要注意，多个依赖的任务，每个任务执行完，都会唤醒当前任务。如果当前任务已经被某个依赖任务执行完毕了，
         * 当下一个依赖任务执行完，还会唤醒当前任务，此时需要注意不能重复处理，第3点保证。
         */

        //6.处理前置有依赖的情况
        //6.1只有一个依赖
        if (dependWrappers.size() == 1) {
            //6-1.1：依赖任务正常结束了，就执行自己
            doDependsOneJob(fromWrapper);
            //6-1.2：开始后继任务
            beginNext(executorService, now, remainTime);
        } else {
            //有多个依赖时
            //6-2.1：多个依赖任务的判断处理
            doDependsJobs(executorService, dependWrappers, fromWrapper, now, remainTime);
        }

    }

    /**
     * 判断自己下游链路上，是否存在已经出结果的或已经开始执行的
     * 如果没有返回true，如果有返回false
     */
    private boolean checkNextWrapperResult() {
        //如果自己就是最后一个，或者后面有并行的多个，就返回true
        if (nextWrappers == null || nextWrappers.size() != 1) {
            return getState() == INIT;
        }
        TaskWrapper nextWrapper = nextWrappers.get(0);
        boolean state = nextWrapper.getState() == INIT;
        //继续校验自己的next的状态
        return state && nextWrapper.checkNextWrapperResult();
    }

    /**
     * 处理多个依赖任务，需要考虑到依赖任务的配置。分为3种情况：
     * 1.依赖任务全部执行完才能执行自己。
     * 2.指定某个依赖任务完成，就可以执行自己。
     * 3.依赖任务都不是must属性，也就是说不是强依赖，此时当前任务会在运行最快的那个依赖任务的线程上执行。
     * <p>
     * 执行流程如下：
     * 1.判断是否有must强依赖，如果没有强依赖，当前任务就可以正常执行了。
     * 2.如果有强依赖，判断依赖任务是否是must？如果不是must的就return了。
     * 3.有强依赖，需要看依赖任务中，是否有超时或者异常的任务，如果有，当前任务也超时、异常，fastFail。
     * 4.有强依赖，判断依赖任务是否全部完成？如果完成了，可以执行当前任务；如果没有完成，return什么也不做。
     * <p>
     * 流程图: https://img.haenu.cn/img/20241119163807.png
     * <p>
     * 注意：
     * 1.多个依赖的任务，每个任务执行完，都会唤醒当前任务。
     * 如果当前任务已经被某个依赖任务执行完毕了，当下一个依赖任务执行完后，还会唤醒当前任务，此时需要注意不能重复处理。task()中的第3点保证了。
     * <p>
     * 2.使用 synchronized 修饰了 doDependsJobs() 方法，保证了避免多线程中的多个依赖任务，使当前任务不能正确执行，或者重复执行。
     */
    private synchronized void doDependsJobs(ExecutorService executorService, List<DependWrapper> dependWrappers, TaskWrapper fromWrapper, long now, long remainTime) {
        //如果当前任务已经完成了，依赖的其他任务拿到锁再进来时，不需要执行下面的逻辑了。
        if (!checkIsNullResult()) {
            return;
        }
        //如果当前依赖是非必须的，跳过不处理
        boolean nowDependIsMust = false;
        //Set统计必须完成的上游wrapper集合
        Set<DependWrapper> mustWrapper = new HashSet<>();
        for (DependWrapper dependWrapper : dependWrappers) {
            if (dependWrapper.isMust()) {
                mustWrapper.add(dependWrapper);
            }
            if (dependWrapper.getDependWrapper().equals(fromWrapper)) {
                nowDependIsMust = dependWrapper.isMust();
            }
        }

        //1.如果全部是不必须的条件，那么只要到了这里，就执行自己。
        if (mustWrapper.size() == 0) {
            //超时处理
            if (ResultState.TIMEOUT == fromWrapper.getTaskResult().getResultState()) {
                fastFail(INIT, null);
            }
            //正常执行情况
            else {
                fire();
            }
            beginNext(executorService, now, remainTime);
            return;
        }

        //2.如果当前依赖是非必须的，跳过不处理（非must情况）
        if (!nowDependIsMust) {
            return;
        }


        //如果fromWrapper是必须的
        boolean existNoFinish = false;
        boolean hasError = false;
        //先判断前面必须要执行的依赖任务的执行结果，如果有任何一个失败，那就不用走action了，直接给自己设置为失败，进行下一步就是了
        for (DependWrapper dependWrapper : mustWrapper) {
            TaskWrapper taskerWrapper = dependWrapper.getDependWrapper();
            TaskResult tempTaskResult = taskerWrapper.getTaskResult();
            //为null或者isTasking，说明它依赖的某个任务还没执行到或没执行完
            if (taskerWrapper.getState() == INIT || taskerWrapper.getState() == WORKING) {
                existNoFinish = true;
                break;
            }
            if (ResultState.TIMEOUT == tempTaskResult.getResultState()) {
                taskResult = defaultResult();
                hasError = true;
                break;
            }
            if (ResultState.EXCEPTION == tempTaskResult.getResultState()) {
                taskResult = defaultExResult(taskerWrapper.getTaskResult().getEx());
                hasError = true;
                break;
            }

        }
        //3.只要有失败、异常的
        if (hasError) {
            fastFail(INIT, null);
            beginNext(executorService, now, remainTime);
            return;
        }

        //如果上游都没有失败，分为两种情况，一种是都finish了，一种是有的在tasking
        //4.依赖任务都完成了，可以执行自己了。
        if (!existNoFinish) {
            fire();
            beginNext(executorService, now, remainTime);
        }
    }

    /**
     * 单依赖的场景，就是依赖任务只有1个。依赖任务和当前任务之间是串行的关系。当前任务依赖于前置任务，
     * 它对依赖任务是 ”生死相依“ 的。如果依赖任务超时，那当前任务也跟着超时；如果依赖任务异常了，
     * 当前任务也跟着异常。只有依赖的任务正常执行完毕了，当前任务才正常执行。
     * <p>
     * 流程图: https://img.haenu.cn/img/20241119162547.png
     * <p>
     * 1.判断依赖任务是否超时，如果超时，则自己也超时。
     * 2.判断依赖任务是否异常，如果异常，则自己也异常。
     * 3.依赖任务正常完成了，则自己正常执行。
     */
    private void doDependsOneJob(TaskWrapper dependWrapper) {
        //1.依赖超时？
        if (ResultState.TIMEOUT == dependWrapper.getTaskResult().getResultState()) {
            taskResult = defaultResult();
            fastFail(INIT, null);
        }
        //2.依赖异常？
        else if (ResultState.EXCEPTION == dependWrapper.getTaskResult().getResultState()) {
            taskResult = defaultExResult(dependWrapper.getTaskResult().getEx());
            fastFail(INIT, null);
        }
        //3.依赖正常
        else {
            //前面任务正常完毕了，该自己了
            fire();
        }
    }

    /**
     * 进行下一个任务
     * 流程图: https://img.haenu.cn/img/20241119161354.png
     * <p>
     * 1.判断当前任务是否有next后续任务，如果没有任务了，就是最后一个任务，就结束了。
     * 2.next后续只有1个任务：判断next任务数量，如果数量只有1个，使用当前任务的线程执行next任务（调用task()方法）
     * 3.next后续有多个任务：判断next任务数量，如果有多个，有几个任务就新起几个线程执行（调用task()方法）
     * 4.阻塞get获取结果。（针对处理next任务有多个的场景）
     * <p>
     * <p>
     * 注意点:
     * 1.beginNext() 中后续任务的处理，也是通过 task() 来处理逻辑的。注意超时时间的处理，
     * 使用 remainTime 剩余时间 - costTime花费时间，这个值在整组任务的执行过程中，是逐渐减小的。例如A、B、C串行执行，
     * 整组任务的超时时间是1000ms，A执行消耗了200ms，到B执行时，B的可用时间 = 1000-200 = 800ms，这个时间是逐渐减小的。
     * 如果这个值小于0了，说明已经超过了整组任务设定的超时时间，任务就 FastFail() 了。
     * <p>
     * 2.beginNext() 中第4点针对后续有多个任务的处理，这里并没有使用带有超时的get方法。单个任务是没有超时监控的，
     * 如果要监控每个任务的超时，就需要一个额外的线程，有几个任务就需要几个线程，高并发场景下，会造成线程 ”爆炸“。全组任务超时，是在Async执行器中控制的。
     */
    private void beginNext(ExecutorService executorService, long now, long remainTime) {
        //花费的时间
        long costTime = SystemClock.now() - now;

        //1.后续没有任务了
        if (nextWrappers == null) {
            return;
        }

        //2.后续只有1个任务，使用当前任务的线程执行next任务
        if (nextWrappers.size() == 1) {
            nextWrappers.get(0).task(executorService, TaskWrapper.this, remainTime - costTime, forParamUseWrappers);
            return;
        }

        //3.后续有多个任务，使用CompletableFuture[]包装，有几个任务就起几个线程执行
        CompletableFuture[] futures = new CompletableFuture[nextWrappers.size()];
        for (int i = 0; i < nextWrappers.size(); i++) {
            int finalI = i;
            futures[i] = CompletableFuture.runAsync(() -> nextWrappers.get(finalI)
                    .task(executorService, TaskWrapper.this, remainTime - costTime, forParamUseWrappers), executorService);
        }

        //4.阻塞获取Future结果，注意这里没有超时时间，超时时间由全局统一控制。
        try {
            CompletableFuture.allOf(futures).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    /**
     * 执行自己的job.具体的执行是在另一个线程里,但判断阻塞超时是在task线程
     */
    private void fire() {
        //阻塞取结果
        taskResult = taskDoJob();
    }

    /**
     * 具体的单个task执行任务
     * 流程图: https://img.haenu.cn/img/20241119155040.png
     * <p>
     * taskDoJob() 具体的单个task任务执行逻辑。主要有7个步骤：
     * 1.Check 重复执行，避免任务重复执行。
     * 2.CAS设置任务状态，state运行状态由 init - > tasking
     * 3.回调 callback.begin()
     * 4.执行耗时操作action
     * 5.CAS设置任务状态，state运行状态由 tasking - > finsh
     * 6.回调 callback.result()
     * 7.异常处理 fastFail()。CAS设置任务状态，state运行状态由 tasking - > finsh；设置默认值、异常信息；
     */
    private TaskResult<V> taskDoJob() {
        //1.Check重复执行
        if (!checkIsNullResult()) {
            return taskResult;
        }
        try {

            /*
             * 2.如果已经不是init状态了，说明正在被执行或已执行完毕。则直接返回
             * 如果当前未init,说明第一次运行 则把状态init - > tasking
             */
            if (!compareAndSetState(INIT, WORKING)) {
                return taskResult;
            }

            //3.回调begin
            callback.begin();

            //4.执行耗时操作action
            V resultValue = task.action(param, forParamUseWrappers);

            //5.设置Wrapper状态为FINISH
            //如果状态不是在tasking,说明别的地方已经修改了
            if (!compareAndSetState(WORKING, FINISH)) {
                return taskResult;
            }

            taskResult.setResultState(ResultState.SUCCESS);
            taskResult.setResult(resultValue);
            //6.回调成功
            callback.result(true, param, taskResult);

            return taskResult;
        } catch (Exception e) {
            //7.异常处理：设置状态ERROR\EXCEPTION，结果设置为默认值
            if (!checkIsNullResult()) {
                return taskResult;
            }
            fastFail(WORKING, e);
            return taskResult;
        }
    }

    /**
     * 停止任务
     */
    public void stopNow() {
        if (getState() == INIT || getState() == WORKING) {
            fastFail(getState(), null);
        }
    }

    /**
     * 快速失败
     */
    private boolean fastFail(int expect, Exception e) {
        //试图将它从expect状态,改成Error
        if (!compareAndSetState(expect, ERROR)) {
            return false;
        }

        //尚未处理过结果-默认状态
        if (checkIsNullResult()) {
            if (e == null) {
                // 将task结果设置为超时状态
                taskResult = defaultResult();
            } else {
                // 将task结果设置为对应异常
                taskResult = defaultExResult(e);
            }
        }
        // 回调result
        callback.result(false, param, taskResult);
        return true;
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

    /**
     * Builder中的属性和TaskWrapper中是一样的，主要是通过build方法，构建TaskWrapper包装类。
     *
     * @param <W>
     * @param <C>
     */
    public static class Builder<W, C> {
        /**
         * 该wrapper的唯一标识
         */
        private String id = UUID.randomUUID().toString();
        /**
         * task将来要处理的param
         */
        private W param;
        private ITask<W, C> task;
        private ICallback<W, C> callback;
        /**
         * 自己后面的所有task
         */
        private List<TaskWrapper<?, ?>> nextWrappers;
        /**
         * 自己依赖的所有task
         */
        private List<DependWrapper> dependWrappers;
        /**
         * 存储强依赖于自己的wrapper集合
         */
        private Set<TaskWrapper<?, ?>> selfIsMustSet;

        private boolean needCheckNextWrapperResult = true;

        public Builder<W, C> task(ITask<W, C> task) {
            this.task = task;
            return this;
        }

        public Builder<W, C> param(W w) {
            this.param = w;
            return this;
        }

        public Builder<W, C> id(String id) {
            if (id != null) {
                this.id = id;
            }
            return this;
        }

        public Builder<W, C> needCheckNextWrapperResult(boolean needCheckNextWrapperResult) {
            this.needCheckNextWrapperResult = needCheckNextWrapperResult;
            return this;
        }

        public Builder<W, C> callback(ICallback<W, C> callback) {
            this.callback = callback;
            return this;
        }

        public Builder<W, C> depend(TaskWrapper<?, ?>... wrappers) {
            if (wrappers == null) {
                return this;
            }
            for (TaskWrapper<?, ?> wrapper : wrappers) {
                depend(wrapper);
            }
            return this;
        }

        public Builder<W, C> depend(TaskWrapper<?, ?> wrapper) {
            return depend(wrapper, true);
        }

        // Builder.depend()：用于绑定当前TaskWrapper任务前置依赖了哪些任务，可以指定依赖项是否必须执行完成，才能执行自己。
        public Builder<W, C> depend(TaskWrapper<?, ?> wrapper, boolean isMust) {
            if (wrapper == null) {
                return this;
            }
            DependWrapper dependWrapper = new DependWrapper(wrapper, isMust);
            if (dependWrappers == null) {
                dependWrappers = new ArrayList<>();
            }
            dependWrappers.add(dependWrapper);
            return this;
        }

        // 用于绑定当前TaskWrapper任务的后置任务节点，可以是多个，后置任务一定是强依赖于自己的。
        public Builder<W, C> next(TaskWrapper<?, ?> wrapper) {
            return next(wrapper, true);
        }

        public Builder<W, C> next(TaskWrapper<?, ?>... wrappers) {
            if (wrappers == null) {
                return this;
            }
            for (TaskWrapper<?, ?> wrapper : wrappers) {
                next(wrapper);
            }
            return this;
        }

        public Builder<W, C> next(TaskWrapper<?, ?> wrapper, boolean selfIsMust) {
            if (nextWrappers == null) {
                nextWrappers = new ArrayList<>();
            }
            nextWrappers.add(wrapper);

            //强依赖自己
            if (selfIsMust) {
                if (selfIsMustSet == null) {
                    selfIsMustSet = new HashSet<>();
                }
                selfIsMustSet.add(wrapper);
            }
            return this;
        }

        // 构建过程。绑定了任务的前置依赖和后置依赖。最终形成任务的相互依赖关系。
        // 图解: https://img.haenu.cn/img/20241119132133.png
        public TaskWrapper<W, C> build() {
            TaskWrapper<W, C> wrapper = new TaskWrapper<>(id, task, param, callback);
            wrapper.setNeedCheckNextWrapperResult(needCheckNextWrapperResult);
            // 1.添加前置依赖
            if (dependWrappers != null) {
                for (DependWrapper dependWrapper : dependWrappers) {
                    dependWrapper.getDependWrapper().addNext(wrapper);
                    wrapper.addDepend(dependWrapper);
                }
            }
            // 2.添加后置依赖
            if (nextWrappers != null) {
                for (TaskWrapper<?, ?> nextWrapper : nextWrappers) {
                    boolean must = false;
                    //检查是否强依赖自己,在next的时候指定了哪些nextWrappers强依赖自己
                    if (selfIsMustSet != null && selfIsMustSet.contains(nextWrapper)) {
                        must = true;
                    }
                    // 2.1.后置任务的前置依赖是自己
                    nextWrapper.addDepend(wrapper, must);
                    // 2.2.添加后置任务
                    wrapper.addNext(nextWrapper);
                }
            }
            return wrapper;
        }
    }
}
