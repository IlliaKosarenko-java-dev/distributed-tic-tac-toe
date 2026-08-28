package com.flamingo.tictactoe.engine.controller;

import java.util.UUID;
import com.flamingo.tictactoe.engine.dto.CreateGameRequest;
import com.flamingo.tictactoe.engine.dto.GameStateResponse;
import com.flamingo.tictactoe.engine.dto.MoveRequest;
import com.flamingo.tictactoe.engine.service.GameCreationResult;
import com.flamingo.tictactoe.engine.service.GameService;
import com.flamingo.tictactoe.engine.repository.StoredGame;
import com.flamingo.tictactoe.engine.domain.Move;
import com.flamingo.tictactoe.engine.domain.Position;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/games")
@Tag(name = "Games", description = "Board state, move validation and outcome detection")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @PostMapping
    @Operation(summary = "Create a game",
            description = "Idempotent on gameId: creating an id that already exists returns "
                    + "200 with the existing game rather than resetting it.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Game created"),
            @ApiResponse(responseCode = "200", description = "Game already existed")
    })
    public ResponseEntity<GameStateResponse> createGame(
            @Valid @RequestBody(required = false) CreateGameRequest request) {
        CreateGameRequest body = request == null ? new CreateGameRequest(null, null) : request;

        GameCreationResult result =
                gameService.createGame(body.gameId(), body.startingPlayerOrDefault());

        return ResponseEntity
                .status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(GameStateResponse.of(result.game()));
    }

    @GetMapping("/{gameId}")
    @Operation(summary = "Read the current board and status")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current state"),
            @ApiResponse(responseCode = "404", description = "No such game")
    })
    public GameStateResponse getGame(@PathVariable UUID gameId) {
        return GameStateResponse.of(gameService.findGame(gameId));
    }

    @PostMapping("/{gameId}/move")
    @Operation(summary = "Play a move",
            description = "Validates the move, updates the board and reports the resulting status.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Move applied"),
            @ApiResponse(responseCode = "400", description = "Position outside the board, or malformed body"),
            @ApiResponse(responseCode = "404", description = "No such game"),
            @ApiResponse(responseCode = "409", description = "Move is illegal in the current state, "
                    + "or the game changed since the caller read it")
    })
    public GameStateResponse move(@PathVariable UUID gameId, @Valid @RequestBody MoveRequest request) {
        Move move = new Move(request.player(), Position.of(request.position()));

        StoredGame updated = gameService.applyMove(gameId, move, request.expectedVersion());
        return GameStateResponse.of(updated, move);
    }
}
