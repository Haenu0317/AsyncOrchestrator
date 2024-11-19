package com.haenu.platform.async.callback.defback;

import com.haenu.platform.async.callback.IGroupCallback;
import com.haenu.platform.async.wrapper.TaskWrapper;

import java.util.List;

public class DefaultGroupCallback implements IGroupCallback {

    @Override
    public void success(List<TaskWrapper> taskWrappers) {

    }

    @Override
    public void failure(List<TaskWrapper> taskWrappers, Exception e) {

    }
}
