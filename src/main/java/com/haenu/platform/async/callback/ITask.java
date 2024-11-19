package com.haenu.platform.async.callback;

import com.haenu.platform.async.wrapper.TaskWrapper;

import java.util.Map;

/**
 * 每个最小执行单元需要实现该接口
 *
 * @author haenu
 * @version 1.0
 * @date 2024/11/18 22:11
 */
@FunctionalInterface

public interface ITask<T, V> {
    /**
     * 执行耗时操作的地方，比如RPC接口调用。
     *
     * @param object      object
     * @param allWrappers 任务包装
     */
    V action(T object, Map<String, TaskWrapper> allWrappers);

    /**
     * 整个Task执行异常，或者超时，会回调defaultValue()。
     *
     * @return 默认值
     */
    default V defaultValue() {
        return null;
    }
}
