package com.flamingo.tictactoe.session.client;

import java.util.UUID;
import com.flamingo.tictactoe.session.client.exception.EngineRejectedException;
import com.flamingo.tictactoe.session.client.exception.EngineUnavailableException;
import com.flamingo.tictactoe.session.domain.GameOutcome;
import com.flamingo.tictactoe.session.domain.Mark;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The gateway against a stubbed engine.
 *
 * <p>Resilience4j is not in play here — these tests pin the translation from HTTP into the two
 * failure types, which is what the retry policy is built on. Whether the retry then fires is
 * covered separately, in a test with a real Spring context.
 */
class GameEngineGatewayTest {

    private static final UUID GAME_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static WireMockServer engine;

    private GameEngineGateway gateway;

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
        gateway = gatewayPointingAt(engine.baseUrl(), Duration.ofSeconds(1));
    }

    private static GameEngineGateway gatewayPointingAt(String baseUrl, Duration readTimeout) {
        RestClient restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(ClientHttpRequestFactories.get(ClientHttpRequestFactorySettings.DEFAULTS
                        .withConnectTimeout(Duration.ofMillis(500))
                        .withReadTimeout(readTimeout)))
                .build();

        GameEngineClient client = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(GameEngineClient.class);

        return new GameEngineGateway(client, new EngineStateMapper());
    }

    private static String gameStateJson(String board, String nextPlayer, String status,
                                        int moveCount, long version) {
        return """
                {"gameId":"11111111-1111-1111-1111-111111111111","board":[%s],"nextPlayer":"%s","status":"%s",
                 "moveCount":%d,"version":%d,"winningLine":null}
                """.formatted(board, nextPlayer, status, moveCount, version);
    }

    @Nested
    class HappyPath {

        @Test
        void createsAGameAndTranslatesTheResponse() {
            engine.stubFor(post(urlPathEqualTo("/games")).willReturn(aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody(gameStateJson("null,null,null,null,null,null,null,null,null",
                            "X", "IN_PROGRESS", 0, 0))));

            EngineGameState state = gateway.createGame(GAME_ID, Mark.X);

            assertThat(state.gameId()).isEqualTo(GAME_ID);
            assertThat(state.nextPlayer()).isEqualTo(Mark.X);
            assertThat(state.outcome()).isEqualTo(GameOutcome.IN_PROGRESS);
            assertThat(state.board().freePositions()).hasSize(9);
            assertThat(state.version()).isZero();

            engine.verify(1, WireMock.postRequestedFor(urlPathEqualTo("/games"))
                    .withRequestBody(matchingJsonPath("$.gameId", equalTo(GAME_ID.toString())))
                    .withRequestBody(matchingJsonPath("$.startingPlayer", equalTo("X"))));
        }

        @Test
        @DisplayName("free cells arrive as null and stay free")
        void translatesABoardWithBothMarksAndFreeCells() {
            engine.stubFor(post(urlPathEqualTo("/games/" + GAME_ID + "/move")).willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(gameStateJson("\"X\",\"O\",null,null,\"X\",null,null,null,null",
                            "O", "IN_PROGRESS", 3, 3))));

            EngineGameState state = gateway.applyMove(GAME_ID, Mark.X, 4, 2L);

            assertThat(state.board().at(0)).contains(Mark.X);
            assertThat(state.board().at(1)).contains(Mark.O);
            assertThat(state.board().at(2)).isEmpty();
            assertThat(state.board().freePositions()).hasSize(6);
            assertThat(state.version()).isEqualTo(3);
        }

        @Test
        void sendsTheExpectedVersionSoTheEngineCanDetectAStaleCaller() {
            engine.stubFor(post(urlPathEqualTo("/games/" + GAME_ID + "/move")).willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(gameStateJson("\"X\",null,null,null,null,null,null,null,null",
                            "O", "IN_PROGRESS", 1, 1))));

            gateway.applyMove(GAME_ID, Mark.X, 0, 0L);

            engine.verify(WireMock.postRequestedFor(urlPathEqualTo("/games/" + GAME_ID + "/move"))
                    .withRequestBody(matchingJsonPath("$.player", equalTo("X")))
                    .withRequestBody(matchingJsonPath("$.position", equalTo("0")))
                    .withRequestBody(matchingJsonPath("$.expectedVersion", equalTo("0"))));
        }

        @Test
        void readsAFinishedGameIncludingItsOutcome() {
            engine.stubFor(get(urlPathEqualTo("/games/" + GAME_ID)).willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                            {"gameId":"11111111-1111-1111-1111-111111111111","board":["X","X","X","O","O",null,null,null,null],
                             "nextPlayer":"O","status":"X_WON","moveCount":5,"version":5,
                             "winningLine":[0,1,2]}
                            """)));

            EngineGameState state = gateway.getGame(GAME_ID);

            assertThat(state.outcome()).isEqualTo(GameOutcome.X_WON);
            assertThat(state.outcome().isTerminal()).isTrue();
            assertThat(state.winningLine()).containsExactly(0, 1, 2);
        }

        @Test
        void ignoresFieldsTheEngineAddsLater() {
            engine.stubFor(get(urlPathEqualTo("/games/" + GAME_ID)).willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                            {"gameId":"11111111-1111-1111-1111-111111111111","board":[null,null,null,null,null,null,null,null,null],
                             "nextPlayer":"X","status":"IN_PROGRESS","moveCount":0,"version":0,
                             "winningLine":null,"someFieldAddedNextYear":"whatever"}
                            """)));

            assertThat(gateway.getGame(GAME_ID).outcome()).isEqualTo(GameOutcome.IN_PROGRESS);
        }
    }

    @Nested
    class RefusalsArePermanent {

        @Test
        void aRejectedMoveCarriesTheEnginesErrorCode() {
            engine.stubFor(post(urlPathEqualTo("/games/" + GAME_ID + "/move")).willReturn(aResponse()
                    .withStatus(409)
                    .withHeader("Content-Type", "application/problem+json")
                    .withBody("""
                            {"type":"https://flamingo.example/errors/cell-occupied",
                             "title":"Move rejected","status":409,
                             "detail":"Position 4 is already taken by X","code":"CELL_OCCUPIED"}
                            """)));

            assertThatThrownBy(() -> gateway.applyMove(GAME_ID, Mark.O, 4, null))
                    .asInstanceOf(InstanceOfAssertFactories.type(EngineRejectedException.class))
                    .satisfies(rejected -> {
                        assertThat(rejected.status()).isEqualTo(409);
                        assertThat(rejected.code()).isEqualTo("CELL_OCCUPIED");
                    });
        }

        @Test
        void anUnknownGameIsAlsoARefusalRatherThanAnOutage() {
            engine.stubFor(get(urlPathEqualTo("/games/" + GAME_ID)).willReturn(aResponse()
                    .withStatus(404)
                    .withHeader("Content-Type", "application/problem+json")
                    .withBody("""
                            {"status":404,"code":"GAME_NOT_FOUND","detail":"No game with id 11111111-1111-1111-1111-111111111111"}
                            """)));

            assertThatThrownBy(() -> gateway.getGame(GAME_ID))
                    .asInstanceOf(InstanceOfAssertFactories.type(EngineRejectedException.class))
                    .extracting(EngineRejectedException::code)
                    .isEqualTo("GAME_NOT_FOUND");
        }

        @Test
        void copesWithARefusalThatHasNoProblemBody() {
            engine.stubFor(post(urlPathEqualTo("/games/" + GAME_ID + "/move"))
                    .willReturn(aResponse().withStatus(400).withBody("not json at all")));

            assertThatThrownBy(() -> gateway.applyMove(GAME_ID, Mark.X, 0, null))
                    .asInstanceOf(InstanceOfAssertFactories.type(EngineRejectedException.class))
                    .extracting(EngineRejectedException::code)
                    .isEqualTo("HTTP_400");
        }
    }

    @Nested
    class OutagesAreTransient {

        @Test
        void aServerErrorIsTreatedAsTransient() {
            engine.stubFor(post(urlPathEqualTo("/games")).willReturn(aResponse().withStatus(503)));

            assertThatThrownBy(() -> gateway.createGame(GAME_ID, Mark.X))
                    .isInstanceOf(EngineUnavailableException.class)
                    .hasMessageContaining("503");
        }

        @Test
        void aReadTimeoutIsTreatedAsTransient() {
            engine.stubFor(get(urlPathEqualTo("/games/" + GAME_ID))
                    .willReturn(aResponse().withStatus(200).withFixedDelay(1500)));

            GameEngineGateway impatient = gatewayPointingAt(engine.baseUrl(), Duration.ofMillis(300));

            assertThatThrownBy(() -> impatient.getGame(GAME_ID))
                    .isInstanceOf(EngineUnavailableException.class)
                    .hasMessageContaining("unreachable");
        }

        @Test
        void aRefusedConnectionIsTreatedAsTransient() {
            // Port 1 is reserved and nothing listens there.
            GameEngineGateway nowhere = gatewayPointingAt("http://localhost:1", Duration.ofMillis(300));

            assertThatThrownBy(() -> nowhere.getGame(GAME_ID))
                    .isInstanceOf(EngineUnavailableException.class);
        }
    }

    @Nested
    class MalformedResponses {

        @Test
        void anUnknownStatusValueFailsClearlyInsteadOfDeepInASimulation() {
            engine.stubFor(get(urlPathEqualTo("/games/" + GAME_ID)).willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(gameStateJson("null,null,null,null,null,null,null,null,null",
                            "X", "SOMETHING_NEW", 0, 0))));

            assertThatThrownBy(() -> gateway.getGame(GAME_ID))
                    .isInstanceOf(EngineUnavailableException.class)
                    .hasMessageContaining("not a known outcome");
        }

        @Test
        void aBoardOfTheWrongSizeIsRejected() {
            engine.stubFor(get(urlPathEqualTo("/games/" + GAME_ID)).willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(gameStateJson("null,null,null", "X", "IN_PROGRESS", 0, 0))));

            assertThatThrownBy(() -> gateway.getGame(GAME_ID))
                    .isInstanceOf(EngineUnavailableException.class)
                    .hasMessageContaining("3 cells");
        }
    }
}
