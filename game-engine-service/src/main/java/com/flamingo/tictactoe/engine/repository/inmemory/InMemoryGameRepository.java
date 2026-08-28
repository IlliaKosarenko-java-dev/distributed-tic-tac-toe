package com.flamingo.tictactoe.engine.repository.inmemory;

import com.flamingo.tictactoe.engine.repository.GameRepository;
import com.flamingo.tictactoe.engine.repository.StoredGame;
import com.flamingo.tictactoe.engine.service.exception.ConcurrentGameUpdateException;
import com.flamingo.tictactoe.engine.service.exception.GameNotFoundException;
import com.flamingo.tictactoe.engine.domain.Game;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.UUID;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps games in a map so the service runs with no infrastructure at all — used by the
 * {@code in-memory} profile and by tests.
 */
@Repository
@Profile("in-memory")
public class InMemoryGameRepository implements GameRepository {

    private final Map<UUID, StoredGame> games = new ConcurrentHashMap<>();

    @Override
    public Optional<StoredGame> findById(UUID gameId) {
        return Optional.ofNullable(games.get(gameId));
    }

    @Override
    public Optional<StoredGame> createIfAbsent(Game game) {
        StoredGame fresh = new StoredGame(game, 0L);
        StoredGame existing = games.putIfAbsent(game.id(), fresh);
        return existing == null ? Optional.of(fresh) : Optional.empty();
    }

    @Override
    public StoredGame save(StoredGame game) {
        UUID gameId = game.game().id();

        // Only the mapping function that actually wins the compare-and-swap records a result,
        // so a losing writer cannot be mistaken for a winner by comparing versions. The
        // `present` flag separates the two ways of losing: the game moved on, or it is gone.
        StoredGame[] written = new StoredGame[1];
        boolean[] present = new boolean[1];
        games.computeIfPresent(gameId, (id, current) -> {
            present[0] = true;
            if (current.version() != game.version()) {
                return current; // someone else moved first; leave their state alone
            }
            written[0] = new StoredGame(game.game(), current.version() + 1);
            return written[0];
        });

        if (written[0] != null) {
            return written[0];
        }
        if (!present[0]) {
            // Telling the caller to retry a game that no longer exists would loop forever.
            throw new GameNotFoundException(gameId);
        }
        throw new ConcurrentGameUpdateException(gameId, game.version());
    }
}
