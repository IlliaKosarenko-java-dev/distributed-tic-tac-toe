package com.flamingo.tictactoe.engine.domain;

public record Move(Player player, Position position) {

    public static Move of(Player player, int index) {
        return new Move(player, Position.of(index));
    }
}
