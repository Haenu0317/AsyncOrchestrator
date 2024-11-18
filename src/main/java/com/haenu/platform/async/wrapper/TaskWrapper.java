package com.haenu.platform.async.wrapper;

import com.haenu.platform.async.callback.ICallback;
import com.haenu.platform.async.callback.ITask;
import com.haenu.platform.async.task.DependWrapper;
import com.haenu.platform.async.task.WorkResult;

import java.util.List;
import java.util.Map;
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
     * worker将来要处理的param
     */
    private T param;

    private ITask<T, V> task;
    private ICallback<T, V> callback;

    private static final int FINISH = 1;
    private static final int ERROR = 2;
    private static final int WORKING = 3;
    private static final int INIT = 0;

    /**
     * 在自己后面的wrapper，如果没有，自己就是末尾；如果有一个，就是串行；如果有多个，有几个就需要开几个线程
     * -------2
     * 1
     * -------3
     * 如1后面有2、3
     */
    private List<TaskWrapper<?, ?>> nextWrappers;
    /**
     * 依赖的wrappers，有2种情况，1:必须依赖的全部完成后，才能执行自己 2:依赖的任何一个、多个完成了，就可以执行自己
     * 通过must字段来控制是否依赖项必须完成
     * 1
     * -------3
     * 2
     * 1、2执行完毕后才能执行3
     */
    private List<DependWrapper> dependWrappers;

    /**
     * 标记该事件是否已经被处理过了，譬如已经超时返回false了，后续rpc又收到返回值了，则不再二次回调
     * 1-finish, 2-error, 3-working
     */
    private AtomicInteger state = new AtomicInteger(0);

    /**
     * 该map存放所有wrapper的id和wrapper映射
     */
    private Map<String, TaskWrapper> forParamUseWrappers;

    /**
     * 用来存临时的结果
     */
    private volatile WorkResult<V> workResult = WorkResult.defaultResult();
}
