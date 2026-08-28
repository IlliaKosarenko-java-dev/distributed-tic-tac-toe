package com.flamingo.tictactoe.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The live stream a browser consumes.
 *
 * <p>{@link ServerSentEvent} does the wire parsing, so these tests assert on event names and
 * payloads rather than on {@code event:}/{@code data:} lines.
 */
class EventStreamIT {

    private static final ParameterizedTypeReference<ServerSentEvent<JsonNode>> EVENT =
            new ParameterizedTypeReference<>() {
            };

    @BeforeAll
    static void startSystem() {
        SystemUnderTest.start();
    }

    private static Flux<ServerSentEvent<JsonNode>> subscribe(String sessionId) {
        return SystemUnderTest.streaming().get()
                .uri("/sessions/{id}/events", sessionId)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(EVENT);
    }

    private static String createSession(long moveDelayMs) {
        return SystemUnderTest.sessions().post().uri("/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"xStrategy":"RULE_BASED","oStrategy":"RANDOM","moveDelayMs":%d}"""
                        .formatted(moveDelayMs))
                .exchange().expectStatus().isCreated()
                .expectBody(JsonNode.class).returnResult().getResponseBody()
                .get("sessionId").asText();
    }

    @Test
    void streamsEveryMoveInOrderAndEndsWithFinished() throws Exception {
        String sessionId = createSession(60);

        // The stream must be live before the game starts, or the opening moves are missed.
        // doOnSubscribe is not enough: it fires before the HTTP request is issued. The snapshot
        // event is the real signal, because the server sends it the moment it accepts a
        // subscriber — so waiting for it makes this ordering deterministic rather than lucky.
        CountDownLatch streamIsLive = new CountDownLatch(1);
        CompletableFuture<List<ServerSentEvent<JsonNode>>> collected = subscribe(sessionId)
                .doOnNext(event -> streamIsLive.countDown())
                .takeUntil(event -> "finished".equals(event.event()) || "error".equals(event.event()))
                .collectList()
                .toFuture();

        assertThat(streamIsLive.await(15, TimeUnit.SECONDS))
                .as("the snapshot event should arrive as soon as the stream is open")
                .isTrue();

        SystemUnderTest.sessions().post().uri("/sessions/{id}/simulate", sessionId)
                .exchange().expectStatus().isAccepted();

        List<ServerSentEvent<JsonNode>> events = collected.get(30, TimeUnit.SECONDS);

        List<String> names = events.stream().map(ServerSentEvent::event).toList();
        assertThat(names).startsWith("snapshot");
        assertThat(names).endsWith("finished");

        List<ServerSentEvent<JsonNode>> moves =
                events.stream().filter(event -> "move".equals(event.event())).toList();
        assertThat(moves).isNotEmpty();

        for (int i = 0; i < moves.size(); i++) {
            JsonNode move = moves.get(i).data();
            assertThat(move.get("seq").asInt())
                    .as("moves must arrive in order, without gaps")
                    .isEqualTo(i + 1);
            assertThat(move.get("player").asText()).isEqualTo(i % 2 == 0 ? "X" : "O");
        }

        JsonNode finished = events.get(events.size() - 1).data();
        assertThat(finished.get("moveCount").asInt()).isEqualTo(moves.size());
        assertThat(finished.get("outcome").asText()).isIn("X_WON", "O_WON", "DRAW");
    }

    @Test
    void theFirstEventCarriesTheCurrentBoardSoALateSubscriberIsNotLeftBlank() {
        String sessionId = createSession(0);
        SystemUnderTest.sessions().post().uri("/sessions/{id}/simulate?mode=sync", sessionId)
                .exchange().expectStatus().isOk();

        // Subscribing after the game is over: without a snapshot this client would wait forever.
        ServerSentEvent<JsonNode> first = subscribe(sessionId)
                .next()
                .block(Duration.ofSeconds(20));

        assertThat(first).isNotNull();
        assertThat(first.event()).isEqualTo("snapshot");
        assertThat(first.data().get("status").asText()).isEqualTo("FINISHED");
        assertThat(first.data().get("moveCount").asInt()).isBetween(5, 9);
    }

    @Test
    void refusesToOpenAStreamForASessionThatDoesNotExist() {
        SystemUnderTest.sessions().get()
                .uri("/sessions/{id}/events", "99999999-9999-9999-9999-999999999999")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isNotFound();
    }
}
