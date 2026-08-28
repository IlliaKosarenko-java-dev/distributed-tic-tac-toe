package com.flamingo.tictactoe.engine.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static com.flamingo.tictactoe.engine.domain.BoardFixtures.board;
import static com.flamingo.tictactoe.engine.domain.BoardFixtures.boardWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoardTest {

    /** The eight ways to win, named as a reader would describe them. */
    static Stream<Arguments> winningLines() {
        return Stream.of(
                Arguments.of("top row", new int[]{0, 1, 2}),
                Arguments.of("middle row", new int[]{3, 4, 5}),
                Arguments.of("bottom row", new int[]{6, 7, 8}),
                Arguments.of("left column", new int[]{0, 3, 6}),
                Arguments.of("middle column", new int[]{1, 4, 7}),
                Arguments.of("right column", new int[]{2, 5, 8}),
                Arguments.of("main diagonal", new int[]{0, 4, 8}),
                Arguments.of("anti diagonal", new int[]{2, 4, 6}));
    }

    @Nested
    class WinDetection {

        @ParameterizedTest(name = "X wins on the {0}")
        @MethodSource("com.flamingo.tictactoe.engine.domain.BoardTest#winningLines")
        void detectsEveryWinningLineForX(String name, int[] line) {
            assertThat(boardWith(Player.X, line).winningLine())
                    .hasValueSatisfying(won -> {
                        assertThat(won.player()).isEqualTo(Player.X);
                        assertThat(won.positions()).containsExactlyInAnyOrder(line[0], line[1], line[2]);
                    });
        }

        @ParameterizedTest(name = "O wins on the {0}")
        @MethodSource("com.flamingo.tictactoe.engine.domain.BoardTest#winningLines")
        void detectsEveryWinningLineForO(String name, int[] line) {
            assertThat(boardWith(Player.O, line).winningLine())
                    .hasValueSatisfying(won -> assertThat(won.player()).isEqualTo(Player.O));
        }

        @Test
        void findsNoLineOnAnEmptyBoard() {
            assertThat(Board.empty().winningLine()).isEmpty();
        }

        @Test
        void doesNotTreatAMixedLineAsAWin() {
            assertThat(board("XOX......").winningLine()).isEmpty();
        }

        @Test
        void doesNotTreatAFullDrawnBoardAsAWin() {
            assertThat(board("XOXXOOOXX").winningLine()).isEmpty();
        }

        @Test
        void ignoresIncompleteLines() {
            assertThat(board("XX.......").winningLine()).isEmpty();
        }
    }

    @Nested
    class Immutability {

        @Test
        void markReturnsANewBoardAndLeavesTheOriginalUntouched() {
            Board original = Board.empty();

            Board updated = original.mark(Position.of(4), Player.X);

            assertThat(updated).isNotSameAs(original);
            assertThat(original.isOccupied(Position.of(4))).isFalse();
            assertThat(updated.isOccupied(Position.of(4))).isTrue();
        }

        @Test
        void cellsSnapshotCannotBeUsedToMutateTheBoard() {
            Board original = board("X........");

            assertThatThrownBy(() -> original.cells().set(1, Player.O))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThat(original.isOccupied(Position.of(1))).isFalse();
        }

        @Test
        void freePositionsIsUnmodifiable() {
            assertThatThrownBy(() -> Board.empty().freePositions().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class Occupancy {

        @Test
        void markingAnOccupiedCellIsAProgrammingError() {
            Board occupied = board("X........");

            assertThatThrownBy(() -> occupied.mark(Position.of(0), Player.O))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already occupied");
        }

        @Test
        void anEmptyBoardHasNineFreeCells() {
            assertThat(Board.empty().freePositions()).hasSize(9);
            assertThat(Board.empty().isFull()).isFalse();
        }

        @Test
        void freeCellsShrinkAsMarksAreAdded() {
            Board afterTwoMoves = board("XO.......");

            assertThat(afterTwoMoves.freePositions())
                    .extracting(Position::index)
                    .containsExactly(2, 3, 4, 5, 6, 7, 8);
        }

        @Test
        void aBoardWithNineMarksIsFull() {
            assertThat(board("XOXXOOOXX").isFull()).isTrue();
            assertThat(board("XOXXOOOXX").freePositions()).isEmpty();
        }

        @Test
        void reportsWhichPlayerOccupiesACell() {
            Board position = board("XO.......");

            assertThat(position.at(Position.of(0))).contains(Player.X);
            assertThat(position.at(Position.of(1))).contains(Player.O);
            assertThat(position.at(Position.of(2))).isEmpty();
        }
    }

    @Nested
    class Construction {

        @ParameterizedTest
        @EnumSource(Player.class)
        void ofRejectsTheWrongNumberOfCells(Player player) {
            assertThatThrownBy(() -> Board.of(player, player))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("exactly 9 cells");
        }

        @Test
        void ofCopiesTheInputSoLaterEditsDoNotLeakIn() {
            Player[] cells = new Player[Board.CELL_COUNT];
            cells[0] = Player.X;
            Board snapshot = Board.of(cells);

            cells[1] = Player.O;

            assertThat(snapshot.isOccupied(Position.of(1))).isFalse();
        }

        @Test
        void boardsWithTheSameCellsAreEqual() {
            assertThat(board("XO.......")).isEqualTo(board("XO......."));
            assertThat(board("XO.......").hashCode()).isEqualTo(board("XO.......").hashCode());
            assertThat(board("XO.......")).isNotEqualTo(board("OX......."));
        }

        @Test
        void fixturesRejectAMalformedSpec() {
            assertThatThrownBy(() -> board("XO."))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("9 characters");
        }
    }
}
