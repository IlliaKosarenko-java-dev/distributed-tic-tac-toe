package com.flamingo.tictactoe.session.client.dto;

public record PlayMoveCommand(String player, int position, Long expectedVersion) {
}
