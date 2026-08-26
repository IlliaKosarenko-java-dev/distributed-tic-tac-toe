package com.flamingo.tictactoe.session.client.dto;

public record CreateGameCommand(String gameId, String startingPlayer) {
}
