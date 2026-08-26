package com.flamingo.tictactoe.session.client;

import com.flamingo.tictactoe.session.client.dto.CreateGameCommand;
import com.flamingo.tictactoe.session.client.dto.EngineGameStateResponse;
import com.flamingo.tictactoe.session.client.dto.PlayMoveCommand;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * The engine's HTTP API as a Java interface. Spring generates the implementation, so the calls
 * are method signatures rather than hand-assembled URLs and bodies.
 */
@HttpExchange
public interface GameEngineClient {

    @PostExchange("/games")
    EngineGameStateResponse createGame(@RequestBody CreateGameCommand command);

    @GetExchange("/games/{gameId}")
    EngineGameStateResponse getGame(@PathVariable String gameId);

    @PostExchange("/games/{gameId}/move")
    EngineGameStateResponse move(@PathVariable String gameId, @RequestBody PlayMoveCommand command);
}
