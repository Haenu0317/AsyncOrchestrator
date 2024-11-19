package com.haenu.platform.async.callback;

public interface ITimeoutTask<T, V> extends ITask<T, V> {
    /**
     * 每个task都可以设置超时时间
     * @return 毫秒超时时间
     */
    long timeOut();

    /**
     * 是否开启单个执行单元的超时功能（有时是一个group设置个超时，而不具备关心单个task的超时）
     *
     * @return 是否开启
     */
    boolean enableTimeOut();
}
