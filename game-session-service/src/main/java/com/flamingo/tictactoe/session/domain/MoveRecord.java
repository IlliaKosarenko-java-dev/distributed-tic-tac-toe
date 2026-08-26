package com.flamingo.tictactoe.session.domain;

import java.time.Instant;

/**
 * One entry in a session's move log.
 *
 * @param seq      1-based order the move was played in
 * @param player   who played it
 * @param position cell 0..8
 * @param at       when the session recorded it
 */
public record MoveRecord(int seq, Mark player, int position, Instant at) {
}
