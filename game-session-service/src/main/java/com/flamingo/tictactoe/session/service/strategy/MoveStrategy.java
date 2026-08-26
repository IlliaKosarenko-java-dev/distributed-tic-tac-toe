package com.flamingo.tictactoe.session.service.strategy;

import com.flamingo.tictactoe.session.domain.BoardSnapshot;
import com.flamingo.tictactoe.session.domain.Mark;
import com.flamingo.tictactoe.session.domain.StrategyType;

/**
 * Picks the next cell for a player. Implementations only ever return a free cell; whether the
 * move is legal in the wider sense remains the engine's ruling.
 */
public interface MoveStrategy {

    StrategyType type();

    /**
     * @return a free cell index, 0..8
     * @throws IllegalStateException if the board has no free cell — the caller should have
     *         stopped at a terminal outcome before asking
     */
    int chooseMove(BoardSnapshot board, Mark player);
}
