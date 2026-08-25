package com.flamingo.tictactoe.engine.service;

import com.flamingo.tictactoe.engine.repository.GameRepository;
import com.flamingo.tictactoe.engine.repository.StoredGame;
import com.flamingo.tictactoe.engine.domain.Game;
import com.flamingo.tictactoe.engine.domain.Move;
import com.flamingo.tictactoe.engine.domain.Player;
import com.flamingo.tictactoe.engine.service.exception.ConcurrentGameUpdateException;
import com.flamingo.tictactoe.engine.service.exception.GameNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates the domain and the store. Holds no rules of its own: every legality decision
 * belongs to {@link Game}, so this class stays a thin read-apply-write.
 */
@Service
public class GameService {

    private static final Logger log = LoggerFactory.getLogger(GameService.class);

    private final GameRepository repository;

    public GameService(GameRepository repository) {
        this.repository = repository;
    }

    /**
     * Creates a game, or returns the existing one untouched if the id is already in use.
     * Idempotent so that a session service retrying a timed-out create does not destroy a
     * game that was in fact created.
     *
     * @return the game, and whether this call is the one that created it
     */
    public GameCreationResult createGame(String requestedId, Player startingPlayer) {
        String gameId = (requestedId == null || requestedId.isBlank())
                ? UUID.randomUUID().toString()
                : requestedId;

        Optional<StoredGame> created = repository.createIfAbsent(Game.newGame(gameId, startingPlayer));
        if (created.isPresent()) {
            log.info("Created game {} starting with {}", gameId, startingPlayer);
            return new GameCreationResult(created.get(), true);
        }

        log.debug("Game {} already exists; returning it unchanged", gameId);
        return new GameCreationResult(requireGame(gameId), false);
    }

    public StoredGame findGame(String gameId) {
        return requireGame(gameId);
    }

    /**
     * Applies a move as a compare-and-swap: read, let the domain decide, write only if the
     * store has not moved on in the meantime.
     *
     * @param expectedVersion optional client-side check; when supplied and already stale the
     *                        move is refused without troubling the domain
     */
    public StoredGame applyMove(String gameId, Move move, Long expectedVersion) {
        StoredGame stored = requireGame(gameId);

        if (expectedVersion != null && expectedVersion != stored.version()) {
            throw new ConcurrentGameUpdateException(gameId, expectedVersion);
        }

        Game updated = stored.game().applyMove(move);
        StoredGame saved = repository.save(stored.withGame(updated));

        log.info("Game {} move {} by {} -> status {} (version {})",
                gameId, move.position().index(), move.player(), updated.status(), saved.version());
        return saved;
    }

    private StoredGame requireGame(String gameId) {
        return repository.findById(gameId).orElseThrow(() -> new GameNotFoundException(gameId));
    }
}
