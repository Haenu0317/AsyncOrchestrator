package com.haenu.platform.async.callback;


import com.haenu.platform.async.wrapper.TaskWrapper;

import java.util.List;

/**
 * 如果是异步执行整组的话，可以用这个组回调。
 */
public interface IGroupCallback {
    /**
     * 成功后，可以从wrapper里去getTaskResult
     */
    void success(List<TaskWrapper> taskWrappers);
    /**
     * 失败了，也可以从wrapper里去getTaskResult
     */
    void failure(List<TaskWrapper> taskWrappers, Exception e);
}
