package com.flamingo.tictactoe.session.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * The pool simulations run on.
 */
@Configuration
public class AsyncConfiguration {

    public static final String SIMULATION_EXECUTOR = "simulationExecutor";

    @Bean(SIMULATION_EXECUTOR)
    public Executor simulationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("simulation-");
        // Let the caller find out immediately rather than block an HTTP thread.
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
