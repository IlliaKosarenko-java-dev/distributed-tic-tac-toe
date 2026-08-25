package com.flamingo.tictactoe.engine.domain;

import com.flamingo.tictactoe.engine.domain.exception.InvalidPositionException;
import com.flamingo.tictactoe.engine.domain.exception.MoveRejectedException;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameTest {

    private static final String GAME_ID = "game-1";

    private static Game newGame() {
        return Game.newGame(GAME_ID, Player.X);
    }

    /**
     * Plays the given positions in order, alternating players from whoever is due to move.
     * Keeps the tests reading as "these cells, in this order" instead of a wall of
     * {@code applyMove} calls.
     */
    private static Game play(Game game, int... positions) {
        Game current = game;
        for (int position : positions) {
            current = current.applyMove(Move.of(current.nextPlayer(), position));
        }
        return current;
    }

    /**
     * A move sequence in which X takes the given line while O takes two unrelated cells.
     * Two marks can never complete a line, so O cannot win first and X cannot win early.
     */
    static Stream<Arguments> xWinningSequences() {
        return BoardTest.winningLines().map(args -> {
            String name = (String) args.get()[0];
            int[] line = (int[]) args.get()[1];

            List<Integer> fillers = new ArrayList<>();
            for (int i = 0; i < Board.CELL_COUNT && fillers.size() < 2; i++) {
                int cell = i;
                if (line[0] != cell && line[1] != cell && line[2] != cell) {
                    fillers.add(cell);
                }
            }
            int[] sequence = {line[0], fillers.get(0), line[1], fillers.get(1), line[2]};
            return Arguments.of(name, sequence, line);
        });
    }

    @Nested
    class NewGame {

        @Test
        void startsEmptyAndInProgress() {
            Game game = newGame();

            assertThat(game.id()).isEqualTo(GAME_ID);
            assertThat(game.board()).isEqualTo(Board.empty());
            assertThat(game.status()).isEqualTo(GameStatus.IN_PROGRESS);
            assertThat(game.status().isTerminal()).isFalse();
            assertThat(game.moveCount()).isZero();
            assertThat(game.winningLine()).isEmpty();
        }

        @Test
        void honoursTheRequestedStartingPlayer() {
            assertThat(Game.newGame(GAME_ID, Player.O).nextPlayer()).isEqualTo(Player.O);
            assertThat(Game.newGame(GAME_ID, Player.X).nextPlayer()).isEqualTo(Player.X);
        }
    }

    @Nested
    class MoveApplication {

        @Test
        void returnsANewGameAndLeavesThePreviousStateUntouched() {
            Game before = newGame();

            Game after = before.applyMove(Move.of(Player.X, 4));

            assertThat(after).isNotSameAs(before);
            assertThat(before.moveCount()).isZero();
            assertThat(before.board()).isEqualTo(Board.empty());
            assertThat(after.moveCount()).isEqualTo(1);
            assertThat(after.board().at(Position.of(4))).contains(Player.X);
        }

        @Test
        void alternatesPlayersAndCountsMoves() {
            Game game = newGame();

            game = game.applyMove(Move.of(Player.X, 0));
            assertThat(game.nextPlayer()).isEqualTo(Player.O);
            assertThat(game.moveCount()).isEqualTo(1);

            game = game.applyMove(Move.of(Player.O, 1));
            assertThat(game.nextPlayer()).isEqualTo(Player.X);
            assertThat(game.moveCount()).isEqualTo(2);
        }

        @Test
        void staysInProgressWhileTheBoardIsUndecided() {
            assertThat(play(newGame(), 0, 1, 2).status()).isEqualTo(GameStatus.IN_PROGRESS);
        }
    }

    @Nested
    class Outcomes {

        @ParameterizedTest(name = "X wins on the {0}")
        @MethodSource("com.flamingo.tictactoe.engine.domain.GameTest#xWinningSequences")
        void detectsAWinOnEveryLine(String name, int[] sequence, int[] line) {
            Game finished = play(newGame(), sequence);

            assertThat(finished.status()).isEqualTo(GameStatus.X_WON);
            assertThat(finished.status().isTerminal()).isTrue();
            assertThat(finished.moveCount()).isEqualTo(5);
            assertThat(finished.winningLine())
                    .hasValueSatisfying(won -> {
                        assertThat(won.player()).isEqualTo(Player.X);
                        assertThat(won.positions()).containsExactlyInAnyOrder(line[0], line[1], line[2]);
                    });
        }

        @Test
        void detectsAWinForO() {
            // X: 3, 4, 6 (not a line); O completes the top row on its third move.
            Game finished = play(newGame(), 3, 0, 4, 1, 6, 2);

            assertThat(finished.status()).isEqualTo(GameStatus.O_WON);
            assertThat(finished.moveCount()).isEqualTo(6);
            assertThat(finished.winningLine())
                    .hasValueSatisfying(won -> assertThat(won.player()).isEqualTo(Player.O));
        }

        @Test
        void detectsADrawWhenTheBoardFillsWithNoLine() {
            Game finished = play(newGame(), 0, 1, 2, 4, 3, 5, 7, 6, 8);

            assertThat(finished.status()).isEqualTo(GameStatus.DRAW);
            assertThat(finished.status().isTerminal()).isTrue();
            assertThat(finished.moveCount()).isEqualTo(9);
            assertThat(finished.board().isFull()).isTrue();
            assertThat(finished.winningLine()).isEmpty();
        }

        @Test
        void aWinOnTheNinthMoveIsAWinNotADraw() {
            // Board fills completely; X's ninth and final mark completes the main diagonal.
            // X: 1, 5, 0, 4, 8   O: 2, 3, 6, 7
            Game finished = play(newGame(), 1, 2, 5, 3, 0, 6, 4, 7, 8);

            assertThat(finished.board().isFull()).isTrue();
            assertThat(finished.status()).isEqualTo(GameStatus.X_WON);
        }
    }

    @Nested
    class Rejections {

        @Test
        void refusesAMoveFromThePlayerWhoseTurnItIsNot() {
            assertThatThrownBy(() -> newGame().applyMove(Move.of(Player.O, 0)))
                    .asInstanceOf(InstanceOfAssertFactories.type(MoveRejectedException.class))
                    .extracting(MoveRejectedException::reason)
                    .isEqualTo(MoveRejectionReason.NOT_PLAYERS_TURN);
        }

        @Test
        void refusesAMoveOntoAnOccupiedCell() {
            Game afterX = newGame().applyMove(Move.of(Player.X, 4));

            assertThatThrownBy(() -> afterX.applyMove(Move.of(Player.O, 4)))
                    .asInstanceOf(InstanceOfAssertFactories.type(MoveRejectedException.class))
                    .extracting(MoveRejectedException::reason)
                    .isEqualTo(MoveRejectionReason.CELL_OCCUPIED);
        }

        @Test
        void refusesAnyMoveOnceTheGameIsWon() {
            Game won = play(newGame(), 0, 3, 1, 4, 2);
            assertThat(won.status()).isEqualTo(GameStatus.X_WON);

            assertThatThrownBy(() -> won.applyMove(Move.of(Player.O, 5)))
                    .asInstanceOf(InstanceOfAssertFactories.type(MoveRejectedException.class))
                    .extracting(MoveRejectedException::reason)
                    .isEqualTo(MoveRejectionReason.GAME_ALREADY_FINISHED);
        }

        @Test
        void refusesAnyMoveOnceTheGameIsDrawn() {
            Game drawn = play(newGame(), 0, 1, 2, 4, 3, 5, 7, 6, 8);
            assertThat(drawn.status()).isEqualTo(GameStatus.DRAW);

            assertThatThrownBy(() -> drawn.applyMove(Move.of(Player.X, 0)))
                    .isInstanceOf(MoveRejectedException.class);
        }

        @Test
        void rejectsAnOutOfRangePositionBeforeAnyRuleIsConsulted() {
            assertThatThrownBy(() -> Move.of(Player.X, 9))
                    .isInstanceOf(InvalidPositionException.class);
        }

        @Test
        void aRejectedMoveLeavesTheGameUnchanged() {
            Game before = newGame().applyMove(Move.of(Player.X, 4));

            assertThatThrownBy(() -> before.applyMove(Move.of(Player.O, 4)))
                    .isInstanceOf(MoveRejectedException.class);

            assertThat(before.moveCount()).isEqualTo(1);
            assertThat(before.nextPlayer()).isEqualTo(Player.O);
            assertThat(before.status()).isEqualTo(GameStatus.IN_PROGRESS);
        }
    }

    @Nested
    class Restoration {

        @Test
        void restoresStoredStateVerbatim() {
            Board board = BoardFixtures.board("XX.OO....");

            Game restored = Game.restore(GAME_ID, board, Player.X, GameStatus.IN_PROGRESS, 4, null);

            assertThat(restored.board()).isEqualTo(board);
            assertThat(restored.moveCount()).isEqualTo(4);
            assertThat(restored.nextPlayer()).isEqualTo(Player.X);
        }

        @Test
        void aRestoredGameCanBePlayedOn() {
            Game restored = Game.restore(GAME_ID, BoardFixtures.board("XX.OO...."),
                    Player.X, GameStatus.IN_PROGRESS, 4, null);

            Game finished = restored.applyMove(Move.of(Player.X, 2));

            assertThat(finished.status()).isEqualTo(GameStatus.X_WON);
            assertThat(finished.moveCount()).isEqualTo(5);
        }
    }
}
