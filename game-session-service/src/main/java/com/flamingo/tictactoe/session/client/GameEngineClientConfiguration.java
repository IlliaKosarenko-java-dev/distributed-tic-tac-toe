package com.flamingo.tictactoe.session.client;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.time.Duration;

/**
 * Wires the declarative engine client.
 *
 * <p>Both timeouts are set explicitly. An unset read timeout is unbounded, which means one
 * unresponsive engine would pin a simulation thread indefinitely and the circuit breaker would
 * never see a failure to count — the outage would look like silence rather than an error.
 */
@Configuration
@EnableConfigurationProperties(GameEngineClientConfiguration.EngineProperties.class)
public class GameEngineClientConfiguration {

    @Bean
    public RestClient gameEngineRestClient(EngineProperties properties, RestClient.Builder builder) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(properties.connectTimeout())
                .withReadTimeout(properties.readTimeout());

        return builder
                .baseUrl(properties.baseUrl())
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();
    }

    @Bean
    public GameEngineClient gameEngineClient(RestClient gameEngineRestClient) {
        return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(gameEngineRestClient))
                .build()
                .createClient(GameEngineClient.class);
    }

    /**
     * @param baseUrl        where the engine lives; the gateway supplies this in a deployed setup
     * @param connectTimeout how long to wait for a connection before giving up
     * @param readTimeout    how long to wait for a response; must stay well below the retry budget
     */
    @ConfigurationProperties(prefix = "tictactoe.engine")
    public record EngineProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {

        public EngineProperties {
            baseUrl = (baseUrl == null || baseUrl.isBlank()) ? "http://localhost:8081" : baseUrl;
            connectTimeout = connectTimeout == null ? Duration.ofSeconds(1) : connectTimeout;
            readTimeout = readTimeout == null ? Duration.ofSeconds(2) : readTimeout;
        }
    }
}
