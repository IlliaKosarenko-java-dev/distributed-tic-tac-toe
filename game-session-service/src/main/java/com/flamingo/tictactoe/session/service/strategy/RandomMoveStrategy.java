package com.flamingo.tictactoe.session.service.strategy;

import com.flamingo.tictactoe.session.domain.BoardSnapshot;
import com.flamingo.tictactoe.session.domain.Mark;
import com.flamingo.tictactoe.session.domain.StrategyType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Random;

/**
 * Picks uniformly among free cells.
 *
 * <p>The {@link Random} is injected rather than created inline so a test can seed it and get a
 * repeatable game — otherwise every assertion about a random player is a coin flip.
 */
@Component
public class RandomMoveStrategy implements MoveStrategy {

    private final Random random;

    public RandomMoveStrategy(Random random) {
        this.random = random;
    }

    @Override
    public StrategyType type() {
        return StrategyType.RANDOM;
    }

    @Override
    public int chooseMove(BoardSnapshot board, Mark player) {
        List<Integer> free = board.freePositions();
        if (free.isEmpty()) {
            throw new IllegalStateException("Asked for a move on a full board");
        }
        return free.get(random.nextInt(free.size()));
    }
}
