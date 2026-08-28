package com.flamingo.tictactoe.session.client;

import java.util.UUID;
import com.flamingo.tictactoe.session.client.exception.EngineRejectedException;
import com.flamingo.tictactoe.session.client.exception.EngineUnavailableException;
import com.flamingo.tictactoe.session.domain.Mark;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Retry and circuit-breaker behaviour with the real Spring context, because the policy lives in
 * aspects and configuration rather than in code a unit test could reach.
 *
 * <p>The load-bearing assertion in this class is the request *count*: it is the only way to tell
 * a retry that fired from one that did not.
 */
@SpringBootTest
@ActiveProfiles("in-memory")
class GameEngineResilienceTest {

    private static final UUID GAME_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String MOVE_PATH = "/games/" + GAME_ID + "/move";

    private static WireMockServer engine;

    @Autowired
    private GameEngineGateway gateway;

    @Autowired
    private CircuitBreakerRegistry circuitBreakers;

    @BeforeAll
    static void startEngine() {
        engine = new WireMockServer(options().dynamicPort());
        engine.start();
    }

    @AfterAll
    static void stopEngine() {
        engine.stop();
    }

    @DynamicPropertySource
    static void pointAtStub(DynamicPropertyRegistry registry) {
        registry.add("tictactoe.engine.base-url", engine::baseUrl);
    }

    @BeforeEach
    void resetEngineAndBreaker() {
        engine.resetAll();
        circuitBreakers.circuitBreaker("gameEngine").reset();
    }

    private static String okBody() {
        return """
                {"gameId":"11111111-1111-1111-1111-111111111111","board":["X",null,null,null,null,null,null,null,null],
                 "nextPlayer":"O","status":"IN_PROGRESS","moveCount":1,"version":1,
                 "winningLine":null}
                """;
    }

    @Test
    void retriesAServerErrorAndSucceedsOnceTheEngineRecovers() {
        engine.stubFor(post(urlPathEqualTo(MOVE_PATH)).inScenario("flaky")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("second"));
        engine.stubFor(post(urlPathEqualTo(MOVE_PATH)).inScenario("flaky")
                .whenScenarioStateIs("second")
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("recovered"));
        engine.stubFor(post(urlPathEqualTo(MOVE_PATH)).inScenario("flaky")
                .whenScenarioStateIs("recovered")
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(okBody())));

        EngineGameState state = gateway.applyMove(GAME_ID, Mark.X, 0, 0L);

        assertThat(state.board().at(0)).contains(Mark.X);
        engine.verify(3, WireMock.postRequestedFor(urlPathEqualTo(MOVE_PATH)));
    }

    @Test
    void givesUpAfterTheConfiguredNumberOfAttempts() {
        engine.stubFor(post(urlPathEqualTo(MOVE_PATH)).willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> gateway.applyMove(GAME_ID, Mark.X, 0, 0L))
                .isInstanceOf(EngineUnavailableException.class);

        engine.verify(3, WireMock.postRequestedFor(urlPathEqualTo(MOVE_PATH)));
    }

    /**
     * The rule this whole phase is built around. A refused move is a verdict; replaying it would
     * produce the same refusal three times over and call the result resilience.
     */
    @Test
    void neverRetriesARejectedMove() {
        engine.stubFor(post(urlPathEqualTo(MOVE_PATH)).willReturn(aResponse()
                .withStatus(409)
                .withHeader("Content-Type", "application/problem+json")
                .withBody("""
                        {"status":409,"code":"CELL_OCCUPIED","detail":"Position 0 is taken"}
                        """)));

        assertThatThrownBy(() -> gateway.applyMove(GAME_ID, Mark.X, 0, 0L))
                .isInstanceOf(EngineRejectedException.class);

        engine.verify(1, WireMock.postRequestedFor(urlPathEqualTo(MOVE_PATH)));
    }

    @Test
    void aRejectedMoveDoesNotCountTowardsOpeningTheBreaker() {
        engine.stubFor(post(urlPathEqualTo(MOVE_PATH)).willReturn(aResponse()
                .withStatus(409)
                .withHeader("Content-Type", "application/problem+json")
                .withBody("""
                        {"status":409,"code":"NOT_PLAYERS_TURN","detail":"It is O's turn"}
                        """)));

        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> gateway.applyMove(GAME_ID, Mark.X, 0, 0L))
                    .isInstanceOf(EngineRejectedException.class);
        }

        assertThat(circuitBreakers.circuitBreaker("gameEngine").getState())
                .as("an engine healthy enough to refuse is not an engine that is down")
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void opensTheBreakerWhenTheEngineKeepsFailing() {
        engine.stubFor(post(urlPathEqualTo(MOVE_PATH)).willReturn(aResponse().withStatus(500)));

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> gateway.applyMove(GAME_ID, Mark.X, 0, 0L))
                    .isInstanceOf(RuntimeException.class);
        }

        assertThat(circuitBreakers.circuitBreaker("gameEngine").getState())
                .isEqualTo(CircuitBreaker.State.OPEN);

        int callsBefore = engine.getAllServeEvents().size();

        assertThatThrownBy(() -> gateway.applyMove(GAME_ID, Mark.X, 0, 0L))
                .isInstanceOf(CallNotPermittedException.class);

        assertThat(engine.getAllServeEvents().size())
                .as("an open breaker must stop hammering an engine that is already struggling")
                .isEqualTo(callsBefore);
    }
}
