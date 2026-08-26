package com.flamingo.tictactoe.session.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.Random;

/**
 * Ambient dependencies as beans rather than static calls, so tests can pin them.
 */
@Configuration
public class SessionConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    public Random random() {
        return new Random();
    }

    @Bean
    @ConditionalOnMissingBean
    public InstanceIdentity instanceIdentity() {
        return InstanceIdentity.generate();
    }
}
