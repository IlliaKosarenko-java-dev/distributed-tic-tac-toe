package com.flamingo.tictactoe.it;

import com.flamingo.tictactoe.engine.GameEngineApplication;
import com.flamingo.tictactoe.session.GameSessionApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.MongoDBContainer;

import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The whole system, started once for the module: one MongoDB, both services on random ports,
 * wired to each other exactly as docker-compose wires them.
 *
 * <p>Both applications run in this JVM but in separate Spring contexts, which is what makes
 * these tests genuinely cross-service: the session service reaches the engine over real HTTP,
 * so the client, the retry policy, the JSON contract and the two databases are all exercised
 * rather than stubbed.
 *
 * <p>Note the two database names on one instance — the same shape as production, where each
 * service also holds credentials scoped to its own database.
 */
final class SystemUnderTest {

    private static final String ENGINE_DB = "tictactoe-games";
    private static final String SESSION_DB = "tictactoe-sessions";

    private static MongoDBContainer mongo;
    private static ConfigurableApplicationContext engine;
    private static ConfigurableApplicationContext session;

    private SystemUnderTest() {
    }

    static synchronized void start() {
        if (session != null) {
            return;
        }
        mongo = new MongoDBContainer("mongo:7");
        mongo.start();

        engine = new SpringApplicationBuilder(GameEngineApplication.class).run(commonArgs(ENGINE_DB));
        session = new SpringApplicationBuilder(GameSessionApplication.class).run(sessionArgs());

        // The JVM outlives the tests; closing here would tear the system down between classes.
        Runtime.getRuntime().addShutdownHook(new Thread(SystemUnderTest::stop));
    }

    /** Restarts only the session service, leaving MongoDB and the engine untouched. */
    static synchronized void restartSessionService() {
        session.close();
        session = new SpringApplicationBuilder(GameSessionApplication.class).run(sessionArgs());
    }

    static String engineUrl() {
        return "http://localhost:" + port(engine);
    }

    static String sessionUrl() {
        return "http://localhost:" + port(session);
    }

    /** Requests and assertions against the session service. */
    static WebTestClient sessions() {
        return client(sessionUrl());
    }

    /** Requests and assertions against the engine, for confirming it agrees. */
    static WebTestClient engine() {
        return client(engineUrl());
    }

    /** Streaming client: {@code ServerSentEvent} parses the wire format so tests need not. */
    static WebClient streaming() {
        return WebClient.create(sessionUrl());
    }

    private static WebTestClient client(String baseUrl) {
        return WebTestClient.bindToServer()
                .baseUrl(baseUrl)
                // A simulation plus two database round trips comfortably exceeds the 5s default.
                .responseTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Passed as command-line arguments rather than through {@code SpringApplicationBuilder
     * .properties(...)}: that method registers *default* properties, the lowest-precedence
     * source, so application.yml would quietly win and both services would talk to
     * localhost:27017 instead of the container.
     */
    private static String[] commonArgs(String database) {
        return new String[]{
                "--server.port=0",
                "--spring.profiles.active=mongo",
                "--spring.data.mongodb.uri=" + mongo.getReplicaSetUrl(database),
                "--spring.main.banner-mode=off",
                // WebFlux is on the test classpath for WebTestClient; both services are
                // servlet-based and this makes sure classpath detection cannot say otherwise.
                "--spring.main.web-application-type=servlet",
                "--logging.level.root=WARN"
        };
    }

    private static String[] sessionArgs() {
        List<String> args = new ArrayList<>(Arrays.asList(commonArgs(SESSION_DB)));
        args.add("--tictactoe.engine.base-url=" + engineUrl());
        return args.toArray(String[]::new);
    }

    private static String port(ConfigurableApplicationContext context) {
        return context.getEnvironment().getProperty("local.server.port");
    }

    private static void stop() {
        if (session != null) {
            session.close();
        }
        if (engine != null) {
            engine.close();
        }
        if (mongo != null) {
            mongo.stop();
        }
    }
}
