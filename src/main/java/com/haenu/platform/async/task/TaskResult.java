package com.haenu.platform.async.task;

/**
 * 执行结果
 */
public class TaskResult<V> {
    /**
     * 执行的结果
     */
    private V result;
    /**
     * 结果状态
     */
    private ResultState resultState;
    private Exception ex;

    public TaskResult(V result, ResultState resultState) {
        this(result, resultState, null);
    }

    public TaskResult(V result, ResultState resultState, Exception ex) {
        this.result = result;
        this.resultState = resultState;
        this.ex = ex;
    }

    public static <V> TaskResult<V> defaultResult() {
        return new TaskResult<>(null, ResultState.DEFAULT);
    }

    @Override
    public String toString() {
        return "TaskResult{" +
                "result=" + result +
                ", resultState=" + resultState +
                ", ex=" + ex +
                '}';
    }

    public Exception getEx() {
        return ex;
    }

    public void setEx(Exception ex) {
        this.ex = ex;
    }

    public V getResult() {
        return result;
    }

    public void setResult(V result) {
        this.result = result;
    }

    public ResultState getResultState() {
        return resultState;
    }

    public void setResultState(ResultState resultState) {
        this.resultState = resultState;
    }
}
