package com.flamingo.tictactoe.session.dto;

import com.flamingo.tictactoe.session.domain.StrategyType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * @param moveDelayMs pause between moves, so the game is watchable rather than instantaneous.
 *                    Capped: a session is claimed for its whole duration, and an hour-long game
 *                    would hold a thread and a claim for an hour.
 */
public record CreateSessionRequest(
        @Schema(description = "Strategy for X. Defaults to RULE_BASED.", example = "RULE_BASED")
        StrategyType xStrategy,

        @Schema(description = "Strategy for O. Defaults to RANDOM.", example = "RANDOM")
        StrategyType oStrategy,

        @Min(value = 0, message = "must not be negative")
        @Max(value = 5_000, message = "must be at most 5000ms")
        @Schema(description = "Milliseconds between moves, 0..5000", example = "400")
        Long moveDelayMs) {

    private static final long DEFAULT_DELAY_MS = 400;

    public StrategyType xStrategyOrDefault() {
        return xStrategy == null ? StrategyType.RULE_BASED : xStrategy;
    }

    public StrategyType oStrategyOrDefault() {
        return oStrategy == null ? StrategyType.RANDOM : oStrategy;
    }

    public long moveDelayMsOrDefault() {
        return moveDelayMs == null ? DEFAULT_DELAY_MS : moveDelayMs;
    }
}
