package com.haenu.platform.async.callback;

import com.haenu.platform.async.task.TaskResult;

/**
 * 每个执行单元执行完毕后，会回调该接口
 * 需要监听执行结果的，实现该接口即可
 */
@FunctionalInterface
public interface ICallback<T, V> {

    /**
     * 任务开始的监听
     * Task开始执行前，先回调begin()
     */
    default void begin() {

    }

    /**
     * action()执行完毕后，回调result方法，可以在此处处理action中的返回值。
     */
    void result(boolean success, T param, TaskResult<V> taskResult);
}
