package com.haenu.platform.async.callback.defback;

import com.haenu.platform.async.callback.ICallback;
import com.haenu.platform.async.task.TaskResult;

/**
 * 默认回调类，如果不设置的话，会默认给这个回调
 *
 * @author haenu
 * @version 1.0
 * @date 2024/11/19 12:07
 */
public class DefaultCallback<T, V> implements ICallback<T, V> {
    @Override
    public void begin() {

    }

    @Override
    public void result(boolean success, T param, TaskResult<V> taskResult) {

    }

}
