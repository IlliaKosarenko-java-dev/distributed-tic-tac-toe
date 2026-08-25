package com.flamingo.tictactoe.engine.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Time as a dependency rather than a static call, so tests can pin it instead of tolerating
 * whatever {@code Instant.now()} returns.
 */
@Configuration
public class ClockConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
