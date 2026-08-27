package com.flamingo.tictactoe.session.controller;

import com.flamingo.tictactoe.session.client.exception.EngineRejectedException;
import com.flamingo.tictactoe.session.client.exception.EngineUnavailableException;
import com.flamingo.tictactoe.session.domain.GameOutcome;
import com.flamingo.tictactoe.session.domain.Mark;
import com.flamingo.tictactoe.session.domain.MoveRecord;
import com.flamingo.tictactoe.session.domain.Session;
import com.flamingo.tictactoe.session.domain.SessionStatus;
import com.flamingo.tictactoe.session.domain.StrategyType;
import com.flamingo.tictactoe.session.repository.SessionQuery;
import com.flamingo.tictactoe.session.repository.StoredSession;
import com.flamingo.tictactoe.session.service.SessionService;
import com.flamingo.tictactoe.session.service.SimulationRunner;
import com.flamingo.tictactoe.session.service.exception.SessionNotFoundException;
import com.flamingo.tictactoe.session.service.exception.SimulationAlreadyStartedException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static com.flamingo.tictactoe.session.domain.BoardFixtures.board;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pins the HTTP contract the UI and the integration tests depend on. */
@WebMvcTest(SessionController.class)
class SessionControllerTest {

    private static final String SESSION_ID = "session-1";
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SessionService sessionService;

    @MockBean
    private SimulationRunner runner;

    @MockBean
    private SseEmitterRegistry emitters;

    private static StoredSession created() {
        return new StoredSession(Session.create(SESSION_ID, SESSION_ID,
                StrategyType.RULE_BASED, StrategyType.RANDOM, 400, NOW), 0L);
    }

    private static StoredSession finished() {
        Session session = Session.create(SESSION_ID, SESSION_ID,
                        StrategyType.RULE_BASED, StrategyType.RANDOM, 0, NOW)
                .claimedBy("instance-a", NOW)
                .withMove(new MoveRecord(1, Mark.X, 4, NOW), board("....X...."), GameOutcome.IN_PROGRESS)
                .withMove(new MoveRecord(2, Mark.O, 0, NOW), board("O...X...."), GameOutcome.X_WON)
                .finished(NOW);
        return new StoredSession(session, 4L);
    }

    @Nested
    class CreateSession {

        @Test
        void returns201WithTheNewSession() throws Exception {
            given(sessionService.createSession(StrategyType.RULE_BASED, StrategyType.RANDOM, 400))
                    .willReturn(created());

            mockMvc.perform(post("/sessions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"xStrategy":"RULE_BASED","oStrategy":"RANDOM","moveDelayMs":400}"""))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.sessionId").value(SESSION_ID))
                    .andExpect(jsonPath("$.gameId").value(SESSION_ID))
                    .andExpect(jsonPath("$.status").value("CREATED"))
                    .andExpect(jsonPath("$.board.length()").value(9))
                    .andExpect(jsonPath("$.board[0]").doesNotExist())
                    .andExpect(jsonPath("$.nextPlayer").value("X"))
                    .andExpect(jsonPath("$.moves").isEmpty());
        }

        @Test
        void appliesDefaultsWhenTheBodyIsAbsent() throws Exception {
            given(sessionService.createSession(any(), any(), org.mockito.ArgumentMatchers.anyLong()))
                    .willReturn(created());

            mockMvc.perform(post("/sessions")).andExpect(status().isCreated());

            verify(sessionService).createSession(StrategyType.RULE_BASED, StrategyType.RANDOM, 400);
        }

        @ParameterizedTest(name = "moveDelayMs {0} is rejected")
        @CsvSource({"-1", "5001", "100000"})
        void rejectsAnOutOfRangeDelay(long delay) throws Exception {
            mockMvc.perform(post("/sessions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"moveDelayMs":%d}""".formatted(delay)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("moveDelayMs"));

            verify(sessionService, never()).createSession(any(), any(), org.mockito.ArgumentMatchers.anyLong());
        }

        @Test
        void rejectsAnUnknownStrategy() throws Exception {
            mockMvc.perform(post("/sessions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"xStrategy":"PSYCHIC"}"""))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class Simulate {

        @Test
        void asyncReturns202AndTheRunningSession() throws Exception {
            given(runner.startAsync(SESSION_ID)).willReturn(created());

            mockMvc.perform(post("/sessions/{id}/simulate", SESSION_ID))
                    .andExpect(status().isAccepted());

            verify(runner).startAsync(SESSION_ID);
        }

        @Test
        void syncReturns200AndTheFinishedSession() throws Exception {
            given(runner.runToCompletion(SESSION_ID)).willReturn(finished());

            mockMvc.perform(post("/sessions/{id}/simulate", SESSION_ID).param("mode", "sync"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("FINISHED"))
                    .andExpect(jsonPath("$.outcome").value("X_WON"))
                    .andExpect(jsonPath("$.moveCount").value(2))
                    .andExpect(jsonPath("$.moves.length()").value(2))
                    .andExpect(jsonPath("$.moves[0].player").value("X"))
                    .andExpect(jsonPath("$.moves[0].position").value(4));
        }

        @Test
        void returns409WhenAnotherRunnerAlreadyOwnsTheSession() throws Exception {
            willThrow(new SimulationAlreadyStartedException(SESSION_ID, SessionStatus.RUNNING))
                    .given(runner).startAsync(SESSION_ID);

            mockMvc.perform(post("/sessions/{id}/simulate", SESSION_ID))
                    .andExpect(status().isConflict())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("SIMULATION_ALREADY_STARTED"))
                    .andExpect(jsonPath("$.currentStatus").value("RUNNING"));
        }

        @Test
        void returns404ForAnUnknownSession() throws Exception {
            willThrow(new SessionNotFoundException("nope")).given(runner).startAsync("nope");

            mockMvc.perform(post("/sessions/{id}/simulate", "nope"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
        }

        @Test
        void returns502WhenTheEngineRefusesTheRequest() throws Exception {
            willThrow(new EngineRejectedException(409, "CELL_OCCUPIED", "taken"))
                    .given(runner).runToCompletion(SESSION_ID);

            mockMvc.perform(post("/sessions/{id}/simulate", SESSION_ID).param("mode", "sync"))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.code").value("ENGINE_REJECTED"))
                    .andExpect(jsonPath("$.engineCode").value("CELL_OCCUPIED"));
        }

        @Test
        void returns503WithRetryAfterWhenTheEngineIsDown() throws Exception {
            willThrow(new EngineUnavailableException("engine down"))
                    .given(runner).runToCompletion(SESSION_ID);

            mockMvc.perform(post("/sessions/{id}/simulate", SESSION_ID).param("mode", "sync"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().string("Retry-After", "5"))
                    .andExpect(jsonPath("$.code").value("ENGINE_UNAVAILABLE"));
        }

        @Test
        void rejectsAnUnknownMode() throws Exception {
            mockMvc.perform(post("/sessions/{id}/simulate", SESSION_ID).param("mode", "sideways"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));

            verify(runner, never()).startAsync(any());
            verify(runner, never()).runToCompletion(any());
        }

        @ParameterizedTest(name = "mode={0} is accepted")
        @CsvSource({"async", "ASYNC", "Async", " async "})
        void acceptsTheModeInAnyCase(String mode) throws Exception {
            given(runner.startAsync(SESSION_ID)).willReturn(created());

            mockMvc.perform(post("/sessions/{id}/simulate", SESSION_ID).param("mode", mode))
                    .andExpect(status().isAccepted());
        }

        @ParameterizedTest(name = "mode={0} runs synchronously")
        @CsvSource({"sync", "SYNC", "Sync"})
        void acceptsSyncInAnyCase(String mode) throws Exception {
            given(runner.runToCompletion(SESSION_ID)).willReturn(finished());

            mockMvc.perform(post("/sessions/{id}/simulate", SESSION_ID).param("mode", mode))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    class ReadSession {

        @Test
        void returnsTheSessionWithItsHistory() throws Exception {
            given(sessionService.findSession(SESSION_ID)).willReturn(finished());

            mockMvc.perform(get("/sessions/{id}", SESSION_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.board[4]").value("X"))
                    .andExpect(jsonPath("$.board[1]").doesNotExist())
                    .andExpect(jsonPath("$.simulationOwner").value("instance-a"))
                    .andExpect(jsonPath("$.version").value(4));
        }

        @Test
        void returns404ForAnUnknownSession() throws Exception {
            given(sessionService.findSession("nope")).willThrow(new SessionNotFoundException("nope"));

            mockMvc.perform(get("/sessions/{id}", "nope"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
        }
    }

    @Nested
    class ListSessions {

        @Test
        void passesTheFiltersStraightThroughToTheQuery() throws Exception {
            given(sessionService.search(any())).willReturn(List.of(finished()));

            mockMvc.perform(get("/sessions")
                            .param("status", "FINISHED")
                            .param("outcome", "X_WON")
                            .param("xStrategy", "RULE_BASED")
                            .param("limit", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].outcome").value("X_WON"));

            verify(sessionService).search(new SessionQuery(SessionStatus.FINISHED,
                    GameOutcome.X_WON, StrategyType.RULE_BASED, null, 5));
        }

        @Test
        void defaultsToTheStandardLimitWithNoFilters() throws Exception {
            given(sessionService.search(any())).willReturn(List.of());

            mockMvc.perform(get("/sessions")).andExpect(status().isOk());

            verify(sessionService).search(new SessionQuery(null, null, null, null,
                    SessionQuery.DEFAULT_LIMIT));
        }
    }

    @Nested
    class EventStream {

        @Test
        void subscribingSendsASnapshotFirstSoALateWatcherSeesTheBoard() throws Exception {
            given(sessionService.findSession(SESSION_ID)).willReturn(finished());
            given(emitters.subscribe(SESSION_ID))
                    .willReturn(new org.springframework.web.servlet.mvc.method.annotation.SseEmitter());

            mockMvc.perform(get("/sessions/{id}/events", SESSION_ID))
                    .andExpect(status().isOk());

            verify(emitters).subscribe(SESSION_ID);
            verify(emitters).publish(any(com.flamingo.tictactoe.session.service.SessionEvent.Snapshot.class));
        }

        @Test
        void doesNotOpenAStreamForAnUnknownSession() throws Exception {
            given(sessionService.findSession("nope")).willThrow(new SessionNotFoundException("nope"));

            mockMvc.perform(get("/sessions/{id}/events", "nope"))
                    .andExpect(status().isNotFound());

            verify(emitters, never()).subscribe(eq("nope"));
        }
    }
}
