package com.flamingo.tictactoe.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A whole game, played by the two services across real HTTP and stored in real MongoDB.
 *
 * <p>Everything below the API is unmocked: the strategies choose, the engine rules, the session
 * records, both databases persist. The client is a test client rather than the session service's
 * own — reusing production client code would let a serialization bug pass unnoticed on both sides.
 */
class FullGameFlowIT {

    @BeforeAll
    static void startSystem() {
        SystemUnderTest.start();
    }

    private static JsonNode createSession(String xStrategy, String oStrategy) {
        return SystemUnderTest.sessions().post().uri("/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"xStrategy":"%s","oStrategy":"%s","moveDelayMs":0}"""
                        .formatted(xStrategy, oStrategy))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody();
    }

    private static JsonNode simulate(String sessionId) {
        return SystemUnderTest.sessions().post()
                .uri("/sessions/{id}/simulate?mode=sync", sessionId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody();
    }

    @Test
    @DisplayName("session creation, simulation and outcome across both services")
    void playsAGameEndToEnd() {
        JsonNode session = createSession("RULE_BASED", "RANDOM");
        String sessionId = session.get("sessionId").asText();

        assertThat(session.get("status").asText()).isEqualTo("CREATED");
        assertThat(session.get("gameId").asText())
                .as("one identifier serves both services")
                .isEqualTo(sessionId);

        JsonNode finished = simulate(sessionId);

        assertThat(finished.get("status").asText()).isEqualTo("FINISHED");
        assertThat(finished.get("outcome").asText()).isIn("X_WON", "O_WON", "DRAW");
        assertThat(finished.get("moveCount").asInt())
                .as("no tic-tac-toe game ends in fewer than five or more than nine moves")
                .isBetween(5, 9);
        assertThat(finished.get("finishedAt").isNull()).isFalse();
        assertThat(finished.get("failureReason").isNull()).isTrue();
    }

    /**
     * The invariant that catches the two services drifting apart: whatever the session says it
     * played must add up to the board the engine actually holds. A desync anywhere — a dropped
     * move, a stale cache, a mis-mapped cell — breaks exactly this assertion.
     */
    @Test
    void theMoveHistoryReplaysToTheSameBoardBothServicesReport() {
        String sessionId = createSession("RULE_BASED", "RANDOM").get("sessionId").asText();
        JsonNode finished = simulate(sessionId);

        List<String> replayed = Boards.replay(finished.get("moves"));

        JsonNode game = SystemUnderTest.engine().get().uri("/games/{id}", sessionId)
                .exchange().expectStatus().isOk()
                .expectBody(JsonNode.class).returnResult().getResponseBody();

        assertThat(replayed).isEqualTo(Boards.of(finished.get("board")));
        assertThat(replayed)
                .as("the engine is the authority; the session's view must match it exactly")
                .isEqualTo(Boards.of(game.get("board")));
        assertThat(game.get("status").asText()).isEqualTo(finished.get("outcome").asText());
        assertThat(game.get("moveCount").asInt()).isEqualTo(finished.get("moveCount").asInt());
    }

    @Test
    void movesAlternateStartingWithX() {
        String sessionId = createSession("RULE_BASED", "RANDOM").get("sessionId").asText();

        JsonNode moves = simulate(sessionId).get("moves");

        for (int i = 0; i < moves.size(); i++) {
            assertThat(moves.get(i).get("seq").asInt()).isEqualTo(i + 1);
            assertThat(moves.get(i).get("player").asText()).isEqualTo(i % 2 == 0 ? "X" : "O");
        }
    }

    @ParameterizedTest(name = "{0} as X vs {1} as O produces a legal game")
    @CsvSource({
            "RULE_BASED, RANDOM",
            "RANDOM, RULE_BASED",
            "RANDOM, RANDOM",
            "RULE_BASED, RULE_BASED"
    })
    void everyStrategyPairingPlaysToAResult(String xStrategy, String oStrategy) {
        String sessionId = createSession(xStrategy, oStrategy).get("sessionId").asText();

        JsonNode finished = simulate(sessionId);

        assertThat(finished.get("status").asText()).isEqualTo("FINISHED");
        assertThat(Boards.replay(finished.get("moves")))
                .isEqualTo(Boards.of(finished.get("board")));
    }

    @Test
    @DisplayName("two rule-based players always draw")
    void aRuleBasedPlayerNeverLosesToItself() {
        String sessionId = createSession("RULE_BASED", "RULE_BASED").get("sessionId").asText();

        assertThat(simulate(sessionId).get("outcome").asText())
                .as("perfect play on both sides has exactly one outcome")
                .isEqualTo("DRAW");
    }

    @Test
    void aSecondSimulationOfTheSameSessionIsRefused() {
        String sessionId = createSession("RANDOM", "RANDOM").get("sessionId").asText();
        simulate(sessionId);

        SystemUnderTest.sessions().post().uri("/sessions/{id}/simulate?mode=sync", sessionId)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.code").isEqualTo("SIMULATION_ALREADY_STARTED");
    }

    @Test
    void anUnknownSessionIsReportedAsMissingByBothEndpoints() {
        String unknown = "99999999-9999-9999-9999-999999999999";

        SystemUnderTest.sessions().get().uri("/sessions/{id}", unknown)
                .exchange().expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo("SESSION_NOT_FOUND");

        SystemUnderTest.engine().get().uri("/games/{id}", unknown)
                .exchange().expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo("GAME_NOT_FOUND");
    }

    @Test
    void finishedSessionsAreQueryableByOutcome() {
        String sessionId = createSession("RULE_BASED", "RULE_BASED").get("sessionId").asText();
        simulate(sessionId);

        JsonNode draws = SystemUnderTest.sessions().get()
                .uri("/sessions?status=FINISHED&outcome=DRAW&limit=100")
                .exchange().expectStatus().isOk()
                .expectBody(JsonNode.class).returnResult().getResponseBody();

        assertThat(draws).isNotEmpty();
        assertThat(draws).allSatisfy(node ->
                assertThat(node.get("outcome").asText()).isEqualTo("DRAW"));
    }
}
