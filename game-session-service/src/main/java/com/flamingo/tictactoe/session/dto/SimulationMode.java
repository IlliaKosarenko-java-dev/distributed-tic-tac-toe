package com.flamingo.tictactoe.session.dto;

import java.util.Locale;

/**
 * How a caller wants a simulation run.
 *
 * <p>{@link #ASYNC} returns as soon as the session is claimed and streams moves over SSE, which
 * is what makes a game watchable. {@link #SYNC} plays the whole game before responding and
 * ignores the configured delay — it exists so a test can assert a final result without waiting
 * out nine real pauses and polling for completion.
 */
public enum SimulationMode {

    ASYNC,
    SYNC;

    /**
     * Parses the {@code mode} query parameter.
     *
     * <p>Case-insensitive on purpose. Spring's built-in String-to-enum binding is not, so
     * {@code ?mode=async} would be rejected while {@code ?mode=ASYNC} worked — a difference no
     * caller would expect and an unpleasant one to debug from a bare 400.
     *
     * @throws IllegalArgumentException with a message naming the accepted values
     */
    public static SimulationMode of(String raw) {
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException unknown) {
            throw new IllegalArgumentException(
                    "mode must be 'async' or 'sync', got '%s'".formatted(raw));
        }
    }
}
