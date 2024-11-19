package com.haenu.platform.async.wrapper;

import com.haenu.platform.async.callback.ICallback;
import com.haenu.platform.async.callback.ITask;
import com.haenu.platform.async.task.DependWrapper;

import java.util.*;

/**
 * Builder中的属性和TaskWrapper中是一样的，主要是通过build方法，构建TaskWrapper包装类。
 *
 * @param <W>
 * @param <C>
 */
public class Builder<W, C> {
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