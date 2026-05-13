package com.im.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;

/**
 * 异步任务线程池配置。
 * 用于消息推送等非关键路径的异步执行，减少请求线程阻塞。
 *
 * <p>面试点：双线程池隔离策略。messagePushExecutor 负责单层异步循环，
 * parallelBatchExecutor 负责大群并行扇出。两者线程池独立，避免
 * @Async 方法内再向同一池提交任务导致的线程池自阻塞。
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("messagePushExecutor")
    public Executor messagePushExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(2000);
        executor.setThreadNamePrefix("msg-push-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * 大群并行扇出线程池，与 messagePushExecutor 独立隔离。
     * {@code asyncPushToMembers} 遇到 100 人以上的大群时，
     * 将成员分批提交到此池并行推送，避免串行 O(N) 阻塞。
     */
    @Bean("parallelBatchExecutor")
    public Executor parallelBatchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(2000);
        executor.setThreadNamePrefix("msg-parallel-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
