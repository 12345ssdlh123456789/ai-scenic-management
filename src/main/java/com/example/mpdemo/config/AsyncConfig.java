package com.example.mpdemo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步线程池配置
 * 用于 OperationLogService 等异步操作
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("asyncExecutor")
    public Executor asyncExecutor(
            @org.springframework.beans.factory.annotation.Value("${async.executor.core-pool-size:5}") int corePoolSize,
            @org.springframework.beans.factory.annotation.Value("${async.executor.max-pool-size:20}") int maxPoolSize,
            @org.springframework.beans.factory.annotation.Value("${async.executor.queue-capacity:100}") int queueCapacity,
            @org.springframework.beans.factory.annotation.Value("${async.executor.thread-name-prefix:AsyncLog-}") String threadNamePrefix,
            @org.springframework.beans.factory.annotation.Value("${async.executor.keep-alive-seconds:60}") int keepAliveSeconds) {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}