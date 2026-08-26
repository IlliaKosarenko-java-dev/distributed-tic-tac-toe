package com.flamingo.tictactoe.session.service.strategy;

import com.flamingo.tictactoe.session.domain.BoardSnapshot;
import com.flamingo.tictactoe.session.domain.Mark;
import com.flamingo.tictactoe.session.domain.StrategyType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.OptionalInt;

/**
 * Plays the classic priority order: win if you can, block if you must, then take the best
 * remaining square.
 *
 * <pre>
 *   1. complete your own line
 *   2. deny the opponent theirs
 *   3. centre
 *   4. a corner
 *   5. a side
 * </pre>
 *
 * <p>Fully deterministic, which is the point: its tests can assert an exact cell rather than
 * a plausible one, and pairing it against {@code RANDOM} makes the two players visibly
 * different rather than two names for the same behaviour.
 */
@Component
public class RuleBasedMoveStrategy implements MoveStrategy {

    private static final int CENTRE = 4;
    private static final List<Integer> CORNERS = List.of(0, 2, 6, 8);
    private static final List<Integer> SIDES = List.of(1, 3, 5, 7);

    @Override
    public StrategyType type() {
        return StrategyType.RULE_BASED;
    }

    @Override
    public int chooseMove(BoardSnapshot board, Mark player) {
        List<Integer> free = board.freePositions();
        if (free.isEmpty()) {
            throw new IllegalStateException("Asked for a move on a full board");
        }

        OptionalInt winning = completingCellFor(board, player);
        if (winning.isPresent()) {
            return winning.getAsInt();
        }

        OptionalInt blocking = completingCellFor(board, player.opponent());
        if (blocking.isPresent()) {
            return blocking.getAsInt();
        }

        if (board.isFree(CENTRE)) {
            return CENTRE;
        }
        return firstFreeOf(board, CORNERS)
                .orElseGet(() -> firstFreeOf(board, SIDES)
                        .orElseThrow(() -> new IllegalStateException("No free cell after all")));
    }

    /** The cell, if any, that would give {@code mark} three in a row right now. */
    private static OptionalInt completingCellFor(BoardSnapshot board, Mark mark) {
        for (int candidate : board.freePositions()) {
            if (board.withMark(candidate, mark).hasLineFor(mark)) {
                return OptionalInt.of(candidate);
            }
        }
        return OptionalInt.empty();
    }

    private static OptionalInt firstFreeOf(BoardSnapshot board, List<Integer> candidates) {
        return candidates.stream().mapToInt(Integer::intValue).filter(board::isFree).findFirst();
    }
}
