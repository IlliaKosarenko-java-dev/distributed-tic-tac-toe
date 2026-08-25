package com.flamingo.tictactoe.engine.service;

import com.flamingo.tictactoe.engine.repository.InMemoryGameRepository;
import com.flamingo.tictactoe.engine.repository.StoredGame;
import com.flamingo.tictactoe.engine.domain.GameStatus;
import com.flamingo.tictactoe.engine.domain.Move;
import com.flamingo.tictactoe.engine.domain.Player;
import com.flamingo.tictactoe.engine.domain.exception.MoveRejectedException;
import com.flamingo.tictactoe.engine.service.exception.ConcurrentGameUpdateException;
import com.flamingo.tictactoe.engine.service.exception.GameNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameServiceTest {

    private static final String GAME_ID = "game-1";

    private GameService service;

    @BeforeEach
    void setUp() {
        service = new GameService(new InMemoryGameRepository());
    }

    @Nested
    class Creation {

        @Test
        void createsAGameAtVersionZero() {
            GameCreationResult result = service.createGame(GAME_ID, Player.X);

            assertThat(result.created()).isTrue();
            assertThat(result.game().version()).isZero();
            assertThat(result.game().game().id()).isEqualTo(GAME_ID);
            assertThat(result.game().game().status()).isEqualTo(GameStatus.IN_PROGRESS);
        }

        @Test
        void generatesAnIdWhenNoneIsSupplied() {
            assertThat(service.createGame(null, Player.X).game().game().id()).isNotBlank();
            assertThat(service.createGame("  ", Player.X).game().game().id()).isNotBlank();
        }

        @Test
        void creatingAnExistingIdReturnsTheGameWithoutResettingIt() {
            service.createGame(GAME_ID, Player.X);
            service.applyMove(GAME_ID, Move.of(Player.X, 4), null);

            GameCreationResult again = service.createGame(GAME_ID, Player.X);

            assertThat(again.created()).isFalse();
            assertThat(again.game().game().moveCount())
                    .as("a retried create must not wipe a game in progress")
                    .isEqualTo(1);
        }
    }

    @Nested
    class Reading {

        @Test
        void readsBackTheCurrentState() {
            service.createGame(GAME_ID, Player.X);

            assertThat(service.findGame(GAME_ID).game().id()).isEqualTo(GAME_ID);
        }

        @Test
        void failsOnAnUnknownGame() {
            assertThatThrownBy(() -> service.findGame("nope"))
                    .isInstanceOf(GameNotFoundException.class);
        }
    }

    @Nested
    class Moves {

        @BeforeEach
        void createGame() {
            service.createGame(GAME_ID, Player.X);
        }

        @Test
        void appliesAMoveAndAdvancesTheVersion() {
            StoredGame afterMove = service.applyMove(GAME_ID, Move.of(Player.X, 4), null);

            assertThat(afterMove.version()).isEqualTo(1);
            assertThat(afterMove.game().moveCount()).isEqualTo(1);
            assertThat(afterMove.game().nextPlayer()).isEqualTo(Player.O);
        }

        @Test
        void reportsTheOutcomeWhenTheGameIsWon() {
            service.applyMove(GAME_ID, Move.of(Player.X, 0), null);
            service.applyMove(GAME_ID, Move.of(Player.O, 3), null);
            service.applyMove(GAME_ID, Move.of(Player.X, 1), null);
            service.applyMove(GAME_ID, Move.of(Player.O, 4), null);

            StoredGame finished = service.applyMove(GAME_ID, Move.of(Player.X, 2), null);

            assertThat(finished.game().status()).isEqualTo(GameStatus.X_WON);
            assertThat(finished.game().winningLine()).isPresent();
        }

        @Test
        void lettingTheDomainRefuseAMoveLeavesTheStoreUntouched() {
            service.applyMove(GAME_ID, Move.of(Player.X, 4), null);

            assertThatThrownBy(() -> service.applyMove(GAME_ID, Move.of(Player.O, 4), null))
                    .isInstanceOf(MoveRejectedException.class);

            assertThat(service.findGame(GAME_ID).version())
                    .as("a refused move must not burn a version")
                    .isEqualTo(1);
        }

        @Test
        void movingOnAnUnknownGameFails() {
            assertThatThrownBy(() -> service.applyMove("nope", Move.of(Player.X, 0), null))
                    .isInstanceOf(GameNotFoundException.class);
        }

        @Test
        void acceptsAMatchingExpectedVersion() {
            assertThat(service.applyMove(GAME_ID, Move.of(Player.X, 0), 0L).version()).isEqualTo(1);
        }

        @Test
        void refusesAStaleExpectedVersionWithoutTouchingTheGame() {
            service.applyMove(GAME_ID, Move.of(Player.X, 0), null);

            assertThatThrownBy(() -> service.applyMove(GAME_ID, Move.of(Player.O, 1), 0L))
                    .isInstanceOf(ConcurrentGameUpdateException.class);

            assertThat(service.findGame(GAME_ID).game().moveCount()).isEqualTo(1);
        }
    }
}
