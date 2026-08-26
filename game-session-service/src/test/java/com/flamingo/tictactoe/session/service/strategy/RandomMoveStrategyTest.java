package com.flamingo.tictactoe.session.service.strategy;

import com.flamingo.tictactoe.session.domain.BoardSnapshot;
import com.flamingo.tictactoe.session.domain.Mark;
import com.flamingo.tictactoe.session.domain.StrategyType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static com.flamingo.tictactoe.session.domain.BoardFixtures.board;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RandomMoveStrategyTest {

    @Test
    void identifiesItself() {
        assertThat(new RandomMoveStrategy(new Random()).type()).isEqualTo(StrategyType.RANDOM);
    }

    @RepeatedTest(20)
    void neverPicksAnOccupiedCell() {
        RandomMoveStrategy strategy = new RandomMoveStrategy(new Random());
        BoardSnapshot crowded = board("XOX.OX.O.");

        int chosen = strategy.chooseMove(crowded, Mark.X);

        assertThat(crowded.isFree(chosen)).isTrue();
    }

    @Test
    void isReproducibleForAGivenSeed() {
        BoardSnapshot empty = BoardSnapshot.empty();

        int first = new RandomMoveStrategy(new Random(42)).chooseMove(empty, Mark.X);
        int second = new RandomMoveStrategy(new Random(42)).chooseMove(empty, Mark.X);

        assertThat(first)
                .as("a seeded strategy must replay identically, or no test of it can assert anything")
                .isEqualTo(second);
    }

    @Test
    void eventuallyReachesEveryFreeCell() {
        RandomMoveStrategy strategy = new RandomMoveStrategy(new Random(7));
        Set<Integer> seen = new HashSet<>();

        for (int i = 0; i < 200; i++) {
            seen.add(strategy.chooseMove(BoardSnapshot.empty(), Mark.X));
        }

        assertThat(seen).hasSize(9);
    }

    @Test
    void refusesToChooseOnAFullBoard() {
        assertThatThrownBy(() ->
                new RandomMoveStrategy(new Random()).chooseMove(board("XOXXOOOXX"), Mark.X))
                .isInstanceOf(IllegalStateException.class);
    }
}
