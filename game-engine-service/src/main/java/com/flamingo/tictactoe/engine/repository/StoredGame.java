package com.flamingo.tictactoe.engine.repository;

import com.flamingo.tictactoe.engine.domain.Game;

/**
 * A game together with the version it was read at.
 *
 * <p>The version is deliberately not part of {@link Game}: it is a concurrency token owned by
 * the store, not a rule of tic-tac-toe. Carrying it alongside the aggregate is what lets a
 * write be a compare-and-swap — read at version N, write only if the store is still at N.
 */
public record StoredGame(Game game, long version) {

    public StoredGame withGame(Game updated) {
        return new StoredGame(updated, version);
    }
}
