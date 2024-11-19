package com.haenu.platform.async.executor;

import com.haenu.platform.async.callback.IGroupCallback;
import com.haenu.platform.async.callback.defback.DefaultGroupCallback;
import com.haenu.platform.async.wrapper.TaskWrapper;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Async执行器执行任务
 *
 * @author haenu
 * @version 1.0
 * @date 2024/11/19 14:13
 */
public class Async {
    /**
     * 默认不定长线程池
     */
    private static final ThreadPoolExecutor COMMON_POOL = (ThreadPoolExecutor) Executors.newCachedThreadPool();

    /**
     * 只能有一个线程池。用户自定义线程池时，也只能定义一个
     */
    private static ExecutorService executorService;

    /**
     * 同步阻塞,直到所有都完成,或失败
     * 如果想自定义线程池，请传pool。不自定义的话，就走默认的COMMON_POOL
     */
    public static boolean beginTask(long timeout, TaskWrapper... taskWrappers) throws ExecutionException, InterruptedException {
        return beginTask(timeout, COMMON_POOL, taskWrappers);
    }

    public static void beginTaskAsync(long timeout, IGroupCallback groupCallback, TaskWrapper... taskWrappers) {
        beginTaskAsync(timeout, COMMON_POOL, groupCallback, taskWrappers);
    }


    public static boolean beginTask(long timeout, ExecutorService executorService, TaskWrapper... taskWrappers) throws ExecutionException, InterruptedException {
        if (taskWrappers == null || taskWrappers.length == 0) {
            return false;
        }
        List<TaskWrapper> taskWrapperList = Arrays.stream(taskWrappers).collect(Collectors.toList());
        return beginTask(timeout, executorService, taskWrapperList);
    }

    /**
     * 出发点
     */
    public static boolean beginTask(long timeout, ExecutorService executorService, List<TaskWrapper> taskWrappers) throws ExecutionException, InterruptedException {
        // 如果我们的工作单元为空 我们则不处理
        if (taskWrappers == null || taskWrappers.size() == 0) {
            return false;
        }
        //保存线程池变量
        Async.executorService = executorService;
        //定义一个map，存放所有的wrapper，key为wrapper的唯一id，value是该wrapper，可以从value中获取wrapper的result
        Map<String, TaskWrapper> forParamUseWrappers = new ConcurrentHashMap<>();
        CompletableFuture[] futures = new CompletableFuture[taskWrappers.size()];
        for (int i = 0; i < taskWrappers.size(); i++) {
            TaskWrapper wrapper = taskWrappers.get(i);
            futures[i] = CompletableFuture.runAsync(() -> wrapper.task(executorService, timeout, forParamUseWrappers), executorService);
        }
        try {
            CompletableFuture.allOf(futures).get(timeout, TimeUnit.MILLISECONDS);
            return true;
        } catch (TimeoutException e) {
            // 超时异常处理
            Set<TaskWrapper> set = new HashSet<>();
            // 递归获取所有的taskWrappers，通过一个Set将所有taskWrappers通过递归的方式统计起来。
            totalTasks(taskWrappers, set);
            // 循环停止所有尚未执行、正在执行的任务。注意已经执行完毕的任务是不处理的（包括异常的）。
            for (TaskWrapper wrapper : set) {
                wrapper.stopNow();
            }
            return false;
        }
    }

    /**
     * 异步执行,直到所有都完成,或失败后，发起回调
     */
    public static void beginTaskAsync(long timeout, ExecutorService executorService, IGroupCallback groupCallback, TaskWrapper... taskWrappers) {
        if (groupCallback == null) {
            groupCallback = new DefaultGroupCallback();
        }
        IGroupCallback finalGroupCallback = groupCallback;
        if (executorService != null) {
            executorService.submit(() -> {
                try {
                    boolean success = beginTask(timeout, executorService, taskWrappers);
                    if (success) {
                        finalGroupCallback.success(Arrays.asList(taskWrappers));
                    } else {
                        finalGroupCallback.failure(Arrays.asList(taskWrappers), new TimeoutException());
                    }
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                    finalGroupCallback.failure(Arrays.asList(taskWrappers), e);
                }
            });
        } else {
            COMMON_POOL.submit(() -> {
                try {
                    boolean success = beginTask(timeout, COMMON_POOL, taskWrappers);
                    if (success) {
                        finalGroupCallback.success(Arrays.asList(taskWrappers));
                    } else {
                        finalGroupCallback.failure(Arrays.asList(taskWrappers), new TimeoutException());
                    }
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                    finalGroupCallback.failure(Arrays.asList(taskWrappers), e);
                }
            });
        }

    }

    /**
     * 总共多少个执行单元
     * 递归获取所有的TaskWrapper，通过一个Set将所有TaskWrapper通过递归的方式统计起来
     */
    @SuppressWarnings("unchecked")
    private static void totalTasks(List<TaskWrapper> taskWrappers, Set<TaskWrapper> set) {
        set.addAll(taskWrappers);
        for (TaskWrapper wrapper : taskWrappers) {
            if (wrapper.getNextWrappers() == null) {
                continue;
            }
            List<TaskWrapper> wrappers = wrapper.getNextWrappers();
            totalTasks(wrappers, set);
        }

    }

    /**
     * 关闭线程池
     */
    public static void shutDown() {
        shutDown(executorService);
    }

    /**
     * 关闭线程池
     */
    public static void shutDown(ExecutorService executorService) {
        if (executorService != null) {
            executorService.shutdown();
        } else {
            COMMON_POOL.shutdown();
        }
    }

    public static String getThreadCount() {
        return "activeCount=" + COMMON_POOL.getActiveCount() +
                "  completedCount " + COMMON_POOL.getCompletedTaskCount() +
                "  largestCount " + COMMON_POOL.getLargestPoolSize();
    }

}
