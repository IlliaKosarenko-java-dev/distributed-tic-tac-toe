package com.flamingo.tictactoe.session.service.strategy;

import com.flamingo.tictactoe.session.domain.BoardSnapshot;
import com.flamingo.tictactoe.session.domain.Mark;
import com.flamingo.tictactoe.session.domain.StrategyType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.flamingo.tictactoe.session.domain.BoardFixtures.board;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Every assertion here names an exact cell. That is only possible because the strategy is
 * deterministic — which is the reason it exists alongside the random one.
 */
class RuleBasedMoveStrategyTest {

    private final RuleBasedMoveStrategy strategy = new RuleBasedMoveStrategy();

    @Test
    void identifiesItself() {
        assertThat(strategy.type()).isEqualTo(StrategyType.RULE_BASED);
    }

    @Nested
    class Winning {

        @ParameterizedTest(name = "completes {1} to win")
        @CsvSource({
                "XX.OO...., 2",   // top row
                "X.XOO...., 1",
                ".XXOO...., 0",
                "XO.XO...., 6",   // left column
                "X...X.OO., 8"    // main diagonal
        })
        void takesTheWinningCellWhenThereIsOne(String spec, int expected) {
            assertThat(strategy.chooseMove(board(spec), Mark.X)).isEqualTo(expected);
        }

        @Test
        void prefersItsOwnWinOverBlockingTheOpponent() {
            // X can finish the top row at 2; O threatens the bottom row at 8.
            BoardSnapshot both = board("XX.O.OOO.");

            assertThat(strategy.chooseMove(both, Mark.X))
                    .as("winning now beats denying a win later")
                    .isEqualTo(2);
        }
    }

    @Nested
    class Blocking {

        @ParameterizedTest(name = "blocks at {1}")
        @CsvSource({
                "OO..X...., 2",   // O would complete the top row
                "O.OX....., 1",
                "O..O.X..., 6"    // O would complete the left column
        })
        void blocksTheOpponentWhenItCannotWin(String spec, int expected) {
            assertThat(strategy.chooseMove(board(spec), Mark.X)).isEqualTo(expected);
        }
    }

    @Nested
    class Positional {

        @Test
        void takesTheCentreOnAnEmptyBoard() {
            assertThat(strategy.chooseMove(BoardSnapshot.empty(), Mark.X)).isEqualTo(4);
        }

        @Test
        void takesACornerWhenTheCentreIsGone() {
            assertThat(strategy.chooseMove(board("....O...."), Mark.X)).isEqualTo(0);
        }

        @Test
        void fallsBackToASideWhenCentreAndCornersAreGone() {
            assertThat(strategy.chooseMove(board("X.X.O.X.X"), Mark.O)).isEqualTo(1);
        }
    }

    @Nested
    class Guards {

        @Test
        void alwaysReturnsAFreeCell() {
            BoardSnapshot crowded = board("XOXOXO.X.");

            int chosen = strategy.chooseMove(crowded, Mark.O);

            assertThat(crowded.isFree(chosen)).isTrue();
        }

        @Test
        void refusesToChooseOnAFullBoard() {
            assertThatThrownBy(() -> strategy.chooseMove(board("XOXXOOOXX"), Mark.X))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("full board");
        }
    }
}
