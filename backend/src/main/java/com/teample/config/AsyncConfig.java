package com.teample.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String TRANSCRIPTION_EXECUTOR = "transcriptionExecutor";

    /**
     * 전사 작업은 외부 STT 응답을 기다리며 오래 걸리므로 요청 스레드와 분리한다.
     * 동시 처리 수는 작게 두고 나머지는 큐에서 대기시킨다.
     */
    @Bean(name = TRANSCRIPTION_EXECUTOR)
    public Executor transcriptionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("transcribe-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
