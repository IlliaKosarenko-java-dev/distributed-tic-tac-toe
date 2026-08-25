package com.flamingo.tictactoe.engine.controller;

import com.flamingo.tictactoe.engine.service.GameCreationResult;
import com.flamingo.tictactoe.engine.service.GameService;
import com.flamingo.tictactoe.engine.service.exception.ConcurrentGameUpdateException;
import com.flamingo.tictactoe.engine.service.exception.GameNotFoundException;
import com.flamingo.tictactoe.engine.repository.StoredGame;
import com.flamingo.tictactoe.engine.domain.Game;
import com.flamingo.tictactoe.engine.domain.GameStatus;
import com.flamingo.tictactoe.engine.domain.Move;
import com.flamingo.tictactoe.engine.domain.MoveRejectionReason;
import com.flamingo.tictactoe.engine.domain.Player;
import com.flamingo.tictactoe.engine.domain.exception.MoveRejectedException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the HTTP contract. The session service branches on these status codes and error
 * codes, so they are a published interface rather than incidental detail.
 */
@WebMvcTest(GameController.class)
class GameControllerTest {

    private static final String GAME_ID = "game-1";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GameService gameService;

    private static StoredGame freshGame() {
        return new StoredGame(Game.newGame(GAME_ID, Player.X), 0L);
    }

    @Nested
    class CreateGame {

        @Test
        void returns201AndTheEmptyBoardWhenCreated() throws Exception {
            given(gameService.createGame(eq(GAME_ID), eq(Player.X)))
                    .willReturn(new GameCreationResult(freshGame(), true));

            mockMvc.perform(post("/games")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"gameId":"game-1","startingPlayer":"X"}"""))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.gameId").value(GAME_ID))
                    .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                    .andExpect(jsonPath("$.version").value(0))
                    .andExpect(jsonPath("$.moveCount").value(0))
                    .andExpect(jsonPath("$.board.length()").value(9))
                    .andExpect(jsonPath("$.board[0]").doesNotExist())
                    .andExpect(jsonPath("$.winningLine").doesNotExist())
                    .andExpect(jsonPath("$.lastMove").doesNotExist());
        }

        @Test
        void returns200WhenTheGameAlreadyExisted() throws Exception {
            given(gameService.createGame(any(), any()))
                    .willReturn(new GameCreationResult(freshGame(), false));

            mockMvc.perform(post("/games")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"gameId":"game-1"}"""))
                    .andExpect(status().isOk());
        }

        @Test
        void defaultsToXWhenNoStartingPlayerIsGiven() throws Exception {
            given(gameService.createGame(any(), eq(Player.X)))
                    .willReturn(new GameCreationResult(freshGame(), true));

            mockMvc.perform(post("/games").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isCreated());

            verify(gameService).createGame(null, Player.X);
        }

        @Test
        void acceptsAnAbsentBody() throws Exception {
            given(gameService.createGame(any(), eq(Player.X)))
                    .willReturn(new GameCreationResult(freshGame(), true));

            mockMvc.perform(post("/games"))
                    .andExpect(status().isCreated());
        }
    }

    @Nested
    class ReadGame {

        @Test
        void returnsTheCurrentState() throws Exception {
            Game played = Game.newGame(GAME_ID, Player.X).applyMove(Move.of(Player.X, 4));
            given(gameService.findGame(GAME_ID)).willReturn(new StoredGame(played, 1L));

            mockMvc.perform(get("/games/{id}", GAME_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.board[4]").value("X"))
                    .andExpect(jsonPath("$.nextPlayer").value("O"))
                    .andExpect(jsonPath("$.version").value(1));
        }

        @Test
        void returns404ForAnUnknownGame() throws Exception {
            given(gameService.findGame("nope")).willThrow(new GameNotFoundException("nope"));

            mockMvc.perform(get("/games/{id}", "nope"))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("GAME_NOT_FOUND"))
                    .andExpect(jsonPath("$.title").value("Game not found"));
        }
    }

    @Nested
    class PlayMove {

        @Test
        void returnsTheUpdatedStateAndEchoesTheMove() throws Exception {
            Game played = Game.newGame(GAME_ID, Player.X).applyMove(Move.of(Player.X, 4));
            given(gameService.applyMove(eq(GAME_ID), any(), any())).willReturn(new StoredGame(played, 1L));

            mockMvc.perform(post("/games/{id}/move", GAME_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"player":"X","position":4}"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.board[4]").value("X"))
                    .andExpect(jsonPath("$.version").value(1))
                    .andExpect(jsonPath("$.lastMove.player").value("X"))
                    .andExpect(jsonPath("$.lastMove.position").value(4));
        }

        @Test
        void reportsTheWinningLineWhenTheGameEnds() throws Exception {
            Game won = Game.newGame(GAME_ID, Player.X)
                    .applyMove(Move.of(Player.X, 0)).applyMove(Move.of(Player.O, 3))
                    .applyMove(Move.of(Player.X, 1)).applyMove(Move.of(Player.O, 4))
                    .applyMove(Move.of(Player.X, 2));
            given(gameService.applyMove(eq(GAME_ID), any(), any())).willReturn(new StoredGame(won, 5L));

            mockMvc.perform(post("/games/{id}/move", GAME_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"player":"X","position":2}"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(GameStatus.X_WON.name()))
                    .andExpect(jsonPath("$.winningLine").isArray())
                    .andExpect(jsonPath("$.winningLine.length()").value(3));
        }

        @ParameterizedTest(name = "{0} -> 409 {1}")
        @CsvSource({
                "CELL_OCCUPIED, CELL_OCCUPIED",
                "NOT_PLAYERS_TURN, NOT_PLAYERS_TURN",
                "GAME_ALREADY_FINISHED, GAME_ALREADY_FINISHED"
        })
        void mapsEveryRejectionReasonToItsOwnErrorCode(String reason, String expectedCode) throws Exception {
            willThrow(new MoveRejectedException(MoveRejectionReason.valueOf(reason), "refused"))
                    .given(gameService).applyMove(eq(GAME_ID), any(), any());

            mockMvc.perform(post("/games/{id}/move", GAME_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"player":"X","position":4}"""))
                    .andExpect(status().isConflict())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value(expectedCode));
        }

        @Test
        void returns409WithVersionConflictWhenTheGameMovedOn() throws Exception {
            willThrow(new ConcurrentGameUpdateException(GAME_ID, 2L))
                    .given(gameService).applyMove(eq(GAME_ID), any(), any());

            mockMvc.perform(post("/games/{id}/move", GAME_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"player":"X","position":4,"expectedVersion":2}"""))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"))
                    .andExpect(jsonPath("$.expectedVersion").value(2));
        }

        @Test
        void returns404WhenTheGameDoesNotExist() throws Exception {
            willThrow(new GameNotFoundException("nope"))
                    .given(gameService).applyMove(eq("nope"), any(), any());

            mockMvc.perform(post("/games/{id}/move", "nope")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"player":"X","position":4}"""))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("GAME_NOT_FOUND"));
        }
    }

    @Nested
    class BadRequests {

        @ParameterizedTest(name = "position {0} is rejected before reaching the service")
        @CsvSource({"-1", "9", "100", "-2147483648", "2147483647"})
        void returns400ForAPositionOutsideTheBoard(int position) throws Exception {
            mockMvc.perform(post("/games/{id}/move", GAME_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"player":"X","position":%d}""".formatted(position)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("position"))
                    .andExpect(jsonPath("$.errors[0].message").value("must be a cell from 0 to 8"))
                    .andExpect(jsonPath("$.errors[0].rejectedValue").value(position));

            verify(gameService, never()).applyMove(any(), any(), any());
        }

        @ParameterizedTest(name = "position {0} is accepted by validation")
        @CsvSource({"0", "4", "8"})
        void allowsEveryCellOnTheBoard(int position) throws Exception {
            given(gameService.applyMove(eq(GAME_ID), any(), any())).willReturn(freshGame());

            mockMvc.perform(post("/games/{id}/move", GAME_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"player":"X","position":%d}""".formatted(position)))
                    .andExpect(status().isOk());
        }

        @Test
        void returns400ForANegativeExpectedVersion() throws Exception {
            mockMvc.perform(post("/games/{id}/move", GAME_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"player":"X","position":4,"expectedVersion":-1}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("expectedVersion"));

            verify(gameService, never()).applyMove(any(), any(), any());
        }

        @Test
        void returns400ForAnUnknownPlayerSymbol() throws Exception {
            mockMvc.perform(post("/games/{id}/move", GAME_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"player":"Z","position":4}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PLAYER"));
        }

        @Test
        void returns400WhenRequiredFieldsAreMissing() throws Exception {
            mockMvc.perform(post("/games/{id}/move", GAME_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors.length()").value(2))
                    .andExpect(jsonPath("$.errors[0].field").value("player"))
                    .andExpect(jsonPath("$.errors[1].field").value("position"));

            verify(gameService, never()).applyMove(any(), any(), any());
        }

        @ParameterizedTest(name = "gameId {0} is rejected")
        @CsvSource({"'has spaces'", "'drop;table'", "'sl/ash'"})
        void returns400ForAGameIdWithUnsafeCharacters(String gameId) throws Exception {
            mockMvc.perform(post("/games")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"gameId":"%s"}""".formatted(gameId)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("gameId"));

            verify(gameService, never()).createGame(any(), any());
        }

        @Test
        void returns400ForAnOverlongGameId() throws Exception {
            mockMvc.perform(post("/games")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"gameId":"%s"}""".formatted("g".repeat(65))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("gameId"));
        }

        @Test
        void returns400ForAnUnparseableBody() throws Exception {
            mockMvc.perform(post("/games/{id}/move", GAME_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{not json"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        }
    }
}
