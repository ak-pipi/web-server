package com.niuma.framework.manager;

import java.util.TimerTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import com.niuma.common.utils.Threads;
import com.niuma.common.utils.spring.SpringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 异步任务管理器
 * 
 * @author ruoyi
 */
public class AsyncManager
{
    private static final Logger logger = LoggerFactory.getLogger(AsyncManager.class);

    /**
     * 操作延迟10毫秒
     */
    private final int OPERATE_DELAY_TIME = 10;

    /**
     * 异步操作任务调度线程池
     */
    private volatile ScheduledExecutorService executor;

    /**
     * 单例模式
     */
    private AsyncManager(){}

    private static AsyncManager me = new AsyncManager();

    public static AsyncManager me()
    {
        return me;
    }

    /**
     * 执行任务
     * 
     * @param task 任务
     */
    public void execute(TimerTask task)
    {
        try
        {
            getExecutor().schedule(task, OPERATE_DELAY_TIME, TimeUnit.MILLISECONDS);
        }
        catch (Exception e)
        {
            logger.error("提交异步任务失败", e);
        }
    }

    /**
     * 停止任务线程池
     */
    public void shutdown()
    {
        Threads.shutdownAndAwaitTermination(executor);
    }

    private ScheduledExecutorService getExecutor()
    {
        ScheduledExecutorService current = executor;
        if (current == null || current.isShutdown() || current.isTerminated())
        {
            synchronized (this)
            {
                current = executor;
                if (current == null || current.isShutdown() || current.isTerminated())
                {
                    executor = SpringUtils.getBean("scheduledExecutorService");
                    current = executor;
                }
            }
        }
        return current;
    }
}
