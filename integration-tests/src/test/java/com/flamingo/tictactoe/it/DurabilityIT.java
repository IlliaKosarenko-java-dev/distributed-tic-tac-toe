package com.flamingo.tictactoe.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The reason a document store was chosen over an in-memory map: a finished game is still there
 * after the process that played it is gone.
 *
 * <p>MongoDB and the engine keep running while the session service is restarted, which is the
 * realistic failure — one service redeployed, everything else untouched.
 */
class DurabilityIT {

    @BeforeAll
    static void startSystem() {
        SystemUnderTest.start();
    }

    private static String createSession(String body) {
        return SystemUnderTest.sessions().post().uri("/sessions")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(body)
                .exchange().expectStatus().isCreated()
                .expectBody(JsonNode.class).returnResult().getResponseBody()
                .get("sessionId").asText();
    }

    private static JsonNode simulate(String sessionId) {
        return SystemUnderTest.sessions().post()
                .uri("/sessions/{id}/simulate?mode=sync", sessionId)
                .exchange().expectStatus().isOk()
                .expectBody(JsonNode.class).returnResult().getResponseBody();
    }

    private static JsonNode read(String sessionId) {
        return SystemUnderTest.sessions().get().uri("/sessions/{id}", sessionId)
                .exchange().expectStatus().isOk()
                .expectBody(JsonNode.class).returnResult().getResponseBody();
    }

    private static JsonNode listSessions() {
        return SystemUnderTest.sessions().get().uri("/sessions?limit=200")
                .exchange().expectStatus().isOk()
                .expectBody(JsonNode.class).returnResult().getResponseBody();
    }

    @Test
    void aFinishedSessionSurvivesARestartWithItsHistoryIntact() {
        String sessionId = createSession("""
                {"xStrategy":"RULE_BASED","oStrategy":"RANDOM","moveDelayMs":0}""");

        JsonNode before = simulate(sessionId);
        assertThat(before.get("status").asText()).isEqualTo("FINISHED");

        SystemUnderTest.restartSessionService();

        JsonNode after = read(sessionId);

        assertThat(after.get("status").asText()).isEqualTo("FINISHED");
        assertThat(after.get("outcome").asText()).isEqualTo(before.get("outcome").asText());
        assertThat(after.get("moveCount").asInt()).isEqualTo(before.get("moveCount").asInt());
        assertThat(Boards.of(after.get("board"))).isEqualTo(Boards.of(before.get("board")));

        assertThat(Boards.replay(after.get("moves")))
                .as("the move log must survive a restart, not just the final board")
                .isEqualTo(Boards.replay(before.get("moves")));
    }

    @Test
    void theEnginesGameOutlivesTheSessionServiceThatDroveIt() {
        String sessionId = createSession("""
                {"moveDelayMs":0}""");
        simulate(sessionId);

        SystemUnderTest.restartSessionService();

        SystemUnderTest.engine().get().uri("/games/{id}", sessionId)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").value(status ->
                        assertThat(status).isIn("X_WON", "O_WON", "DRAW"));
    }

    @Test
    void sessionsCreatedBeforeARestartCanStillBeListed() {
        createSession("""
                {"moveDelayMs":0}""");
        int before = listSessions().size();

        SystemUnderTest.restartSessionService();

        assertThat(listSessions().size()).isEqualTo(before);
    }
}
