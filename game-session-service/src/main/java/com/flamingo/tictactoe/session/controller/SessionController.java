package com.flamingo.tictactoe.session.controller;

import java.util.UUID;
import com.flamingo.tictactoe.session.domain.GameOutcome;
import com.flamingo.tictactoe.session.domain.SessionStatus;
import com.flamingo.tictactoe.session.domain.StrategyType;
import com.flamingo.tictactoe.session.dto.CreateSessionRequest;
import com.flamingo.tictactoe.session.dto.SessionResponse;
import com.flamingo.tictactoe.session.dto.SimulationMode;
import com.flamingo.tictactoe.session.repository.SessionQuery;
import com.flamingo.tictactoe.session.repository.StoredSession;
import com.flamingo.tictactoe.session.service.SessionEvent;
import com.flamingo.tictactoe.session.service.SessionService;
import com.flamingo.tictactoe.session.service.SimulationRunner;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/sessions")
@Tag(name = "Sessions", description = "Game sessions and the automated play that drives them")
public class SessionController {

    private final SessionService sessionService;
    private final SimulationRunner runner;
    private final SseEmitterRegistry emitters;

    public SessionController(SessionService sessionService, SimulationRunner runner,
                             SseEmitterRegistry emitters) {
        this.sessionService = sessionService;
        this.runner = runner;
        this.emitters = emitters;
    }

    @PostMapping
    @Operation(summary = "Create a session",
            description = "Chooses a strategy per player. The session id doubles as the game id.")
    public ResponseEntity<SessionResponse> create(
            @Valid @RequestBody(required = false) CreateSessionRequest request) {

        CreateSessionRequest body = request == null
                ? new CreateSessionRequest(null, null, null)
                : request;

        StoredSession created = sessionService.createSession(
                body.xStrategyOrDefault(), body.oStrategyOrDefault(), body.moveDelayMsOrDefault());

        return ResponseEntity.status(HttpStatus.CREATED).body(SessionResponse.of(created));
    }

    @PostMapping("/{sessionId}/simulate")
    @Operation(summary = "Run the game",
            description = "async (default) returns 202 immediately and streams moves over SSE. "
                    + "sync plays the whole game before responding and ignores the move delay.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Simulation started"),
            @ApiResponse(responseCode = "200", description = "Simulation finished (sync)"),
            @ApiResponse(responseCode = "404", description = "No such session"),
            @ApiResponse(responseCode = "409", description = "Already claimed by a runner")
    })
    public ResponseEntity<SessionResponse> simulate(
            @PathVariable UUID sessionId,
            @RequestParam(defaultValue = "async") String mode) {

        // Parsed rather than bound directly to the enum: Spring's String-to-enum conversion is
        // case-sensitive, so ?mode=async would be a 400 while ?mode=ASYNC worked.
        if (SimulationMode.of(mode) == SimulationMode.SYNC) {
            return ResponseEntity.ok(SessionResponse.of(runner.runToCompletion(sessionId)));
        }
        return ResponseEntity.accepted().body(SessionResponse.of(runner.startAsync(sessionId)));
    }

    @GetMapping("/{sessionId}")
    @Operation(summary = "Read a session, its board and its full move history")
    public SessionResponse get(@PathVariable UUID sessionId) {
        return SessionResponse.of(sessionService.findSession(sessionId));
    }

    @GetMapping
    @Operation(summary = "List sessions, newest first",
            description = "Filterable by status, outcome and either player's strategy — which is "
                    + "how you find out whether RULE_BASED actually beats RANDOM.")
    public List<SessionResponse> list(
            @RequestParam(required = false) SessionStatus status,
            @RequestParam(required = false) GameOutcome outcome,
            @RequestParam(required = false) StrategyType xStrategy,
            @RequestParam(required = false) StrategyType oStrategy,
            @RequestParam(defaultValue = "20") int limit) {

        return sessionService.search(new SessionQuery(status, outcome, xStrategy, oStrategy, limit))
                .stream()
                .map(SessionResponse::of)
                .toList();
    }

    /**
     * Streams moves as they are played. The first event is always a snapshot, so a client that
     * attaches mid-game — or after it ended — sees the current board instead of waiting for a
     * move that may never come.
     */
    @GetMapping(value = "/{sessionId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Subscribe to a session's progress",
            description = "Events: snapshot, status, move, finished, error")
    public SseEmitter events(@PathVariable UUID sessionId) {
        StoredSession stored = sessionService.findSession(sessionId);

        SseEmitter emitter = emitters.subscribe(sessionId);
        emitters.publish(new SessionEvent.Snapshot(sessionId, stored.session().status(),
                stored.session().gameOutcome(), stored.session().board().cells(),
                stored.session().moveCount()));

        return emitter;
    }

}

