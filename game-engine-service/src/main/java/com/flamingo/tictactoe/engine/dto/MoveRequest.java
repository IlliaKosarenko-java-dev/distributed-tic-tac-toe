package com.flamingo.tictactoe.engine.dto;

import com.flamingo.tictactoe.engine.domain.Board;
import com.flamingo.tictactoe.engine.domain.Player;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * @param player          X or O. A symbol outside that set fails during deserialization rather
 *                        than here — Jackson rejects it before the record exists, so
 *                        {@code @NotNull} only covers a missing value.
 * @param position        cell 0..8, bounds-checked before the request reaches the domain
 * @param expectedVersion optional optimistic-concurrency check. When supplied and already
 *                        stale, the move is refused with 409, which turns a duplicated
 *                        request into an error instead of a second move.
 */
public record MoveRequest(
        @NotNull(message = "is required and must be X or O")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "X")
        Player player,

        @NotNull(message = "is required")
        @Min(value = 0, message = "must be a cell from 0 to 8")
        @Max(value = Board.CELL_COUNT - 1, message = "must be a cell from 0 to 8")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0", maximum = "8", example = "4")
        Integer position,

        @PositiveOrZero(message = "must not be negative")
        @Schema(description = "Version the caller last read; omit to skip the check", example = "3")
        Long expectedVersion) {
}
