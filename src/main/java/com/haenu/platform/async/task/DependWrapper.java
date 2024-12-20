package com.haenu.platform.async.task;


import com.haenu.platform.async.wrapper.TaskWrapper;

/**
 * 对依赖的wrapper的封装
 */
public class DependWrapper {
    private TaskWrapper<?, ?> dependWrapper;
    /**
     * 是否该依赖必须完成后才能执行自己.
     * 因为存在一个任务，依赖于多个任务，是让这多个任务全部完成后才执行自己，还是某几个执行完毕就可以执行自己
     * 如
     * 1
     * ---3
     * 2
     * 或
     * 1---3
     * 2---3
     * 这两种就不一样，上面的就是必须12都完毕，才能3
     * 下面的就是1完毕就可以3
     */
    private boolean must = true;

    public DependWrapper(TaskWrapper<?, ?> dependWrapper, boolean must) {
        this.dependWrapper = dependWrapper;
        this.must = must;
    }

    public DependWrapper() {
    }

    public TaskWrapper<?, ?> getDependWrapper() {
        return dependWrapper;
    }

    public void setDependWrapper(TaskWrapper<?, ?> dependWrapper) {
        this.dependWrapper = dependWrapper;
    }

    public boolean isMust() {
        return must;
    }

    public void setMust(boolean must) {
        this.must = must;
    }

    @Override
    public String toString() {
        return "DependWrapper{" +
                "dependWrapper=" + dependWrapper +
                ", must=" + must +
                '}';
    }
}
