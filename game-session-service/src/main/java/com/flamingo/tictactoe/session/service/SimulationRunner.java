package com.flamingo.tictactoe.session.service;

import java.util.UUID;
import com.flamingo.tictactoe.session.client.EngineGameState;
import com.flamingo.tictactoe.session.client.GameEngineGateway;
import com.flamingo.tictactoe.session.client.exception.EngineException;
import com.flamingo.tictactoe.session.client.exception.EngineRejectedException;
import com.flamingo.tictactoe.session.config.AsyncConfiguration;
import com.flamingo.tictactoe.session.domain.BoardSnapshot;
import com.flamingo.tictactoe.session.domain.Mark;
import com.flamingo.tictactoe.session.domain.Session;
import com.flamingo.tictactoe.session.repository.StoredSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;

/**
 * Drives a session to a result: pick a cell, send it to the engine, record what came back,
 * repeat until the engine says the game is over.
 *
 * <p>The engine is the only authority on the board. This loop never decides that a move was
 * legal or that a game has ended — it asks, and believes the answer.
 */
@Service
public class SimulationRunner {

    /** A tic-tac-toe game cannot exceed nine moves; more means something is badly wrong. */
    private static final int MOVE_CEILING = BoardSnapshot.CELL_COUNT;

    private static final Logger log = LoggerFactory.getLogger(SimulationRunner.class);

    private final SessionService sessionService;
    private final GameEngineGateway engine;
    private final SessionEventPublisher events;
    private final Executor executor;

    public SimulationRunner(SessionService sessionService, GameEngineGateway engine,
                            SessionEventPublisher events,
                            @Qualifier(AsyncConfiguration.SIMULATION_EXECUTOR) Executor executor) {
        this.sessionService = sessionService;
        this.engine = engine;
        this.events = events;
        this.executor = executor;
    }

    /**
     * Claims the session and hands the game to a background thread.
     *
     * <p>The claim happens on the caller's thread on purpose: a caller that has lost the race
     * should be told so in its own response, not discover it in a log line later.
     */
    public StoredSession startAsync(UUID sessionId) {
        StoredSession claimed = sessionService.claimForSimulation(sessionId);
        events.publish(new SessionEvent.StatusChanged(sessionId, claimed.session().status()));
        executor.execute(() -> play(claimed, true));
        return claimed;
    }

    /**
     * Plays the whole game before returning, ignoring the configured delay.
     *
     * <p>Exists for tests: waiting out nine real delays and polling for completion makes for a
     * slow test and a flaky one.
     */
    public StoredSession runToCompletion(UUID sessionId) {
        StoredSession claimed = sessionService.claimForSimulation(sessionId);
        return play(claimed, false);
    }

    private StoredSession play(StoredSession claimed, boolean pauseBetweenMoves) {
        UUID sessionId = claimed.session().sessionId();
        StoredSession current = claimed;

        try {
            // Idempotent: a session whose game already exists picks up where the engine left off.
            EngineGameState state = engine.createGame(claimed.session().gameId(), Mark.X);

            while (!state.outcome().isTerminal()) {
                if (current.session().moveCount() >= MOVE_CEILING) {
                    throw new IllegalStateException(
                            "Session %s reached %d moves without a result".formatted(sessionId, MOVE_CEILING));
                }

                Mark player = state.nextPlayer();
                int position = sessionService.chooseMove(current.session(), player, state.board());

                state = engine.applyMove(claimed.session().gameId(), player, position, state.version());
                current = sessionService.recordMove(current, player, position, state.board(), state.outcome());

                events.publish(new SessionEvent.MoveMade(sessionId, current.session().moveCount(),
                        player, position, state.board().cells(), state.outcome(), state.version()));

                if (pauseBetweenMoves) {
                    pause(current.session());
                }
            }

            current = sessionService.markFinished(current);
            events.publish(new SessionEvent.Finished(sessionId, state.outcome(),
                    current.session().moveCount(), state.winningLine()));
            return current;

        } catch (EngineRejectedException rejected) {
            // The runner only ever picks cells the engine itself reported as free, so a refusal
            // means the two views of the board have diverged. Failing loudly beats trying another
            // cell and playing on from a position that is already wrong.
            return fail(current, rejected.code(), rejected.getMessage());

        } catch (EngineException unavailable) {
            return fail(current, "ENGINE_UNAVAILABLE", unavailable.getMessage());

        } catch (RuntimeException unexpected) {
            log.error("Simulation for session {} failed unexpectedly", sessionId, unexpected);
            return fail(current, "SIMULATION_ERROR", unexpected.getMessage());
        } finally {
            events.closeStream(sessionId);
        }
    }

    private StoredSession fail(StoredSession current, String code, String message) {
        StoredSession failed = sessionService.markFailed(current, "%s: %s".formatted(code, message));
        events.publish(new SessionEvent.Failed(current.session().sessionId(), code, message));
        return failed;
    }

    private void pause(Session session) {
        if (session.moveDelayMs() <= 0) {
            return;
        }
        try {
            Thread.sleep(session.moveDelayMs());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Simulation interrupted", interrupted);
        }
    }
}
