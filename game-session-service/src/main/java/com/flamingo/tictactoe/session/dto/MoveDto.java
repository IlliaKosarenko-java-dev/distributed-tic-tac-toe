package com.flamingo.tictactoe.session.dto;

import com.flamingo.tictactoe.session.domain.Mark;
import com.flamingo.tictactoe.session.domain.MoveRecord;

import java.time.Instant;

public record MoveDto(int seq, Mark player, int position, Instant at) {

    public static MoveDto of(MoveRecord record) {
        return new MoveDto(record.seq(), record.player(), record.position(), record.at());
    }
}
