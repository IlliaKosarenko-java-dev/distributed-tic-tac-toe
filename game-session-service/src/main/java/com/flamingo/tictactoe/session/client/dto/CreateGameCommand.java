package com.flamingo.tictactoe.session.client.dto;

import java.util.UUID;

public record CreateGameCommand(UUID gameId, String startingPlayer) {
}
