package com.flamingo.tictactoe.session.service;

import com.flamingo.tictactoe.session.client.EngineStateMapper;
import com.flamingo.tictactoe.session.client.GameEngineClient;
import com.flamingo.tictactoe.session.client.GameEngineGateway;
import com.flamingo.tictactoe.session.config.InstanceIdentity;
import com.flamingo.tictactoe.session.domain.GameOutcome;
import com.flamingo.tictactoe.session.domain.SessionStatus;
import com.flamingo.tictactoe.session.domain.StrategyType;
import com.flamingo.tictactoe.session.repository.StoredSession;
import com.flamingo.tictactoe.session.repository.inmemory.InMemorySessionRepository;
import com.flamingo.tictactoe.session.service.exception.SimulationAlreadyStartedException;
import com.flamingo.tictactoe.session.service.strategy.MoveStrategies;
import com.flamingo.tictactoe.session.service.strategy.RandomMoveStrategy;
import com.flamingo.tictactoe.session.service.strategy.RuleBasedMoveStrategy;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Random;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The loop against a stubbed engine.
 *
 * <p>Runs synchronously throughout: a test that waits out real move delays and polls for
 * completion is slow and flaky, and neither property is worth paying for here.
 */
class SimulationRunnerTest {

    private static WireMockServer engine;

    private SessionService sessionService;
    private SimulationRunner runner;
    private RecordingEventPublisher publisher;

    @BeforeAll
    static void startEngine() {
        engine = new WireMockServer(options().dynamicPort());
        engine.start();
    }

    @AfterAll
    static void stopEngine() {
        engine.stop();
    }

    @BeforeEach
    void setUp() {
        engine.resetAll();

        sessionService = new SessionService(
                new InMemorySessionRepository(),
                new MoveStrategies(List.of(new RandomMoveStrategy(new Random(11)),
                        new RuleBasedMoveStrategy())),
                new InstanceIdentity("instance-test"),
                Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC));

        publisher = new RecordingEventPublisher();

        RestClient restClient = RestClient.builder()
                .baseUrl(engine.baseUrl())
                .requestFactory(ClientHttpRequestFactories.get(ClientHttpRequestFactorySettings.DEFAULTS
                        .withConnectTimeout(Duration.ofMillis(500))
                        .withReadTimeout(Duration.ofSeconds(1))))
                .build();
        GameEngineClient client = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient)).build()
                .createClient(GameEngineClient.class);

        runner = new SimulationRunner(sessionService,
                new GameEngineGateway(client, new EngineStateMapper()),
                publisher,
                Runnable::run);
    }

    /** Serves a scripted game: each move returns the next board the engine would report. */
    private void stubGame(List<String> boards, List<String> statuses) {
        engine.stubFor(post(urlPathEqualTo("/games")).willReturn(json(201,
                body("null,null,null,null,null,null,null,null,null", "X", "IN_PROGRESS", 0, 0, "null"))));

        String scenario = "game";
        String state = com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
        for (int i = 0; i < boards.size(); i++) {
            String next = "move-" + (i + 1);
            String nextPlayer = i % 2 == 0 ? "O" : "X";
            String winningLine = "X_WON".equals(statuses.get(i)) || "O_WON".equals(statuses.get(i))
                    ? "[0,1,2]" : "null";
            engine.stubFor(post(urlPathMatching("/games/.*/move")).inScenario(scenario)
                    .whenScenarioStateIs(state)
                    .willReturn(json(200, body(boards.get(i), nextPlayer, statuses.get(i), i + 1, i + 1, winningLine)))
                    .willSetStateTo(next));
            state = next;
        }
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(int status, String body) {
        return aResponse().withStatus(status)
                .withHeader("Content-Type", "application/json").withBody(body);
    }

    private static String body(String board, String nextPlayer, String status,
                               int moveCount, long version, String winningLine) {
        return """
                {"gameId":"g","board":[%s],"nextPlayer":"%s","status":"%s","moveCount":%d,
                 "version":%d,"winningLine":%s}
                """.formatted(board, nextPlayer, status, moveCount, version, winningLine);
    }

    private StoredSession createSession() {
        return sessionService.createSession(StrategyType.RULE_BASED, StrategyType.RANDOM, 0);
    }

    @Nested
    class CompletedGames {

        @Test
        void playsUntilTheEngineReportsAWinAndRecordsEveryMove() {
            stubGame(
                    List.of("null,null,null,null,\"X\",null,null,null,null",
                            "\"O\",null,null,null,\"X\",null,null,null,null",
                            "\"O\",null,null,null,\"X\",null,null,null,\"X\"",
                            "\"O\",\"O\",null,null,\"X\",null,null,null,\"X\"",
                            "\"O\",\"O\",null,null,\"X\",null,\"X\",null,\"X\""),
                    List.of("IN_PROGRESS", "IN_PROGRESS", "IN_PROGRESS", "IN_PROGRESS", "X_WON"));

            StoredSession finished = runner.runToCompletion(createSession().session().sessionId());

            assertThat(finished.session().status()).isEqualTo(SessionStatus.FINISHED);
            assertThat(finished.session().gameOutcome()).isEqualTo(GameOutcome.X_WON);
            assertThat(finished.session().moveCount()).isEqualTo(5);
            assertThat(finished.session().moves())
                    .as("history must be complete and sequentially numbered")
                    .extracting("seq").containsExactly(1, 2, 3, 4, 5);
        }

        @Test
        void emitsAMoveEventPerMoveThenFinished() {
            stubGame(
                    List.of("null,null,null,null,\"X\",null,null,null,null",
                            "\"O\",null,null,null,\"X\",null,null,null,null",
                            "\"O\",null,null,null,\"X\",null,null,null,\"X\""),
                    List.of("IN_PROGRESS", "IN_PROGRESS", "X_WON"));

            runner.runToCompletion(createSession().session().sessionId());

            assertThat(publisher.eventNames()).containsExactly("move", "move", "move", "finished");
            assertThat(publisher.ofType(SessionEvent.Finished.class)).singleElement()
                    .satisfies(finished -> {
                        assertThat(finished.outcome()).isEqualTo(GameOutcome.X_WON);
                        assertThat(finished.winningLine()).containsExactly(0, 1, 2);
                    });
        }

        @Test
        void closesTheEventStreamWhenTheGameEnds() {
            stubGame(List.of("null,null,null,null,\"X\",null,null,null,null"), List.of("DRAW"));

            String sessionId = createSession().session().sessionId();
            runner.runToCompletion(sessionId);

            assertThat(publisher.closedStreams())
                    .as("a stream left open keeps a browser waiting forever")
                    .containsExactly(sessionId);
        }
    }

    @Nested
    class Failures {

        @Test
        void anEngineOutageEndsTheSessionAsFailedWithAReason() {
            engine.stubFor(post(urlPathEqualTo("/games")).willReturn(aResponse().withStatus(503)));

            StoredSession failed = runner.runToCompletion(createSession().session().sessionId());

            assertThat(failed.session().status()).isEqualTo(SessionStatus.FAILED);
            assertThat(failed.session().failureReason())
                    .get().asString().contains("ENGINE_UNAVAILABLE");
            assertThat(publisher.eventNames()).containsExactly("error");
        }

        /**
         * The runner only ever picks cells the engine itself reported as free, so a refusal means
         * the two views have diverged. It must stop, not try elsewhere.
         */
        @Test
        void aRefusedMoveFailsTheSessionRatherThanTryingAnotherCell() {
            engine.stubFor(post(urlPathEqualTo("/games")).willReturn(json(201,
                    body("null,null,null,null,null,null,null,null,null", "X", "IN_PROGRESS", 0, 0, "null"))));
            engine.stubFor(post(urlPathMatching("/games/.*/move")).willReturn(aResponse()
                    .withStatus(409)
                    .withHeader("Content-Type", "application/problem+json")
                    .withBody("""
                            {"status":409,"code":"CELL_OCCUPIED","detail":"Position 4 is taken"}
                            """)));

            StoredSession failed = runner.runToCompletion(createSession().session().sessionId());

            assertThat(failed.session().status()).isEqualTo(SessionStatus.FAILED);
            assertThat(failed.session().failureReason()).get().asString().contains("CELL_OCCUPIED");
            engine.verify(1, com.github.tomakehurst.wiremock.client.WireMock
                    .postRequestedFor(urlPathMatching("/games/.*/move")));
        }

        @Test
        void aFailureStillClosesTheEventStream() {
            engine.stubFor(post(urlPathEqualTo("/games")).willReturn(aResponse().withStatus(500)));

            String sessionId = createSession().session().sessionId();
            runner.runToCompletion(sessionId);

            assertThat(publisher.closedStreams()).containsExactly(sessionId);
        }
    }

    @Nested
    class Claiming {

        @Test
        void aSecondRunOnTheSameSessionIsRefused() {
            stubGame(List.of("null,null,null,null,\"X\",null,null,null,null"), List.of("DRAW"));
            String sessionId = createSession().session().sessionId();
            runner.runToCompletion(sessionId);

            assertThatThrownBy(() -> runner.runToCompletion(sessionId))
                    .isInstanceOf(SimulationAlreadyStartedException.class);
        }

        @Test
        void startAsyncPublishesTheStatusChangeBeforeAnyMove() {
            stubGame(List.of("null,null,null,null,\"X\",null,null,null,null"), List.of("DRAW"));

            runner.startAsync(createSession().session().sessionId());

            assertThat(publisher.eventNames()).startsWith("status");
        }
    }
}
