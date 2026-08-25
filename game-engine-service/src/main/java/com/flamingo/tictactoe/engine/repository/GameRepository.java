package com.flamingo.tictactoe.engine.repository;

import com.flamingo.tictactoe.engine.service.exception.ConcurrentGameUpdateException;
import com.flamingo.tictactoe.engine.domain.Game;

import java.util.Optional;

/**
 * The engine's only outbound port. Implemented in-memory for local runs and tests, and by
 * MongoDB in production — the compare-and-swap contract on {@link #save} is what both
 * adapters must honour, and is what keeps concurrent moves correct across replicas.
 */
public interface GameRepository {

    Optional<StoredGame> findById(String gameId);

    /**
     * Stores the game only if no game with that id exists yet.
     *
     * @return the stored game, or empty if the id was already taken — which makes
     *         {@code POST /games} idempotent without a read-then-write race
     */
    Optional<StoredGame> createIfAbsent(Game game);

    /**
     * @param game the game to store, carrying the version it was read at
     * @return the game at its new version
     * @throws ConcurrentGameUpdateException if the store has moved on since that read
     */
    StoredGame save(StoredGame game);
}
