package com.flamingo.tictactoe.session.client.exception;

/**
 * Something went wrong talking to the engine.
 *
 * <p>The split between the two subtypes is the whole retry policy: {@link EngineUnavailableException}
 * means "ask again and it may work", {@link EngineRejectedException} means "the engine considered
 * this and said no". Encoding that in the type is what lets the retry configuration name a single
 * exception class instead of enumerating status codes and hoping the list stays right.
 */
public abstract class EngineException extends RuntimeException {

    protected EngineException(String message, Throwable cause) {
        super(message, cause);
    }

    protected EngineException(String message) {
        super(message);
    }
}
