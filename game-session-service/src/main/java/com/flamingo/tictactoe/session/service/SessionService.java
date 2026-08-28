package com.flamingo.tictactoe.session.service;

import com.flamingo.tictactoe.session.config.InstanceIdentity;
import com.flamingo.tictactoe.session.domain.BoardSnapshot;
import com.flamingo.tictactoe.session.domain.GameOutcome;
import com.flamingo.tictactoe.session.domain.Mark;
import com.flamingo.tictactoe.session.domain.MoveRecord;
import com.flamingo.tictactoe.session.domain.Session;
import com.flamingo.tictactoe.session.domain.StrategyType;
import com.flamingo.tictactoe.session.repository.SessionQuery;
import com.flamingo.tictactoe.session.repository.SessionRepository;
import com.flamingo.tictactoe.session.repository.StoredSession;
import com.flamingo.tictactoe.session.service.exception.SessionNotFoundException;
import com.flamingo.tictactoe.session.service.exception.SimulationAlreadyStartedException;
import com.flamingo.tictactoe.session.service.strategy.MoveStrategies;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Session lifecycle and move selection. Deliberately knows nothing about HTTP or about the
 * engine — driving the loop is the runner's job, added in a later phase.
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final SessionRepository repository;
    private final MoveStrategies strategies;
    private final InstanceIdentity instance;
    private final Clock clock;

    public SessionService(SessionRepository repository, MoveStrategies strategies,
                          InstanceIdentity instance, Clock clock) {
        this.repository = repository;
        this.strategies = strategies;
        this.instance = instance;
        this.clock = clock;
    }

    /**
     * The session id doubles as the game id, so a session and the game it drives share one
     * identifier and neither needs a lookup table to find the other.
     */
    public StoredSession createSession(StrategyType xStrategy, StrategyType oStrategy, long moveDelayMs) {
        UUID sessionId = UUID.randomUUID();
        Session session = Session.create(sessionId, sessionId, xStrategy, oStrategy,
                moveDelayMs, clock.instant());

        StoredSession created = repository.create(session);
        log.info("Created session {} ({} as X, {} as O, {}ms between moves)",
                sessionId, xStrategy, oStrategy, moveDelayMs);
        return created;
    }

    public List<StoredSession> search(SessionQuery query) {
        return repository.search(query);
    }

    public StoredSession findSession(UUID sessionId) {
        return repository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));
    }

    /**
     * Takes ownership of a session so exactly one runner drives it.
     *
     * @throws SimulationAlreadyStartedException if another caller already claimed it
     */
    public StoredSession claimForSimulation(UUID sessionId) {
        StoredSession existing = findSession(sessionId);

        return repository.claimForSimulation(sessionId, instance.id(), clock.instant())
                .map(claimed -> {
                    log.info("Session {} claimed by {}", sessionId, instance.id());
                    return claimed;
                })
                .orElseThrow(() -> new SimulationAlreadyStartedException(
                        sessionId, existing.session().status()));
    }

    /**
     * Picks a cell for the given player from the given board.
     *
     * <p>The board is a parameter rather than read off the session because the engine's latest
     * response is the authoritative position; the session's copy is a mirror for reads.
     */
    public int chooseMove(Session session, Mark player, BoardSnapshot board) {
        return strategies.of(session.strategyFor(player)).chooseMove(board, player);
    }

    /** Convenience for callers with nothing fresher than the session's own cached board. */
    public int chooseNextMove(Session session) {
        return chooseMove(session, session.nextPlayer(), session.board());
    }

    /** Appends a move and refreshes the cached board from what the engine reported. */
    public StoredSession recordMove(StoredSession stored, Mark player, int position,
                                    BoardSnapshot board, GameOutcome outcome) {
        MoveRecord move = new MoveRecord(
                stored.session().moveCount() + 1, player, position, clock.instant());

        return repository.save(stored.withSession(
                stored.session().withMove(move, board, outcome)));
    }

    public StoredSession markFinished(StoredSession stored) {
        log.info("Session {} finished: {}", stored.session().sessionId(), stored.session().gameOutcome());
        return repository.save(stored.withSession(stored.session().finished(clock.instant())));
    }

    public StoredSession markFailed(StoredSession stored, String reason) {
        log.warn("Session {} failed: {}", stored.session().sessionId(), reason);
        return repository.save(stored.withSession(stored.session().failed(reason, clock.instant())));
    }
}
