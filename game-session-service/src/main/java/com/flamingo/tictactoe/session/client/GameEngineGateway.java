package com.flamingo.tictactoe.session.client;

import com.flamingo.tictactoe.session.client.dto.CreateGameCommand;
import com.flamingo.tictactoe.session.client.dto.EngineGameStateResponse;
import com.flamingo.tictactoe.session.client.dto.PlayMoveCommand;
import com.flamingo.tictactoe.session.client.exception.EngineException;
import com.flamingo.tictactoe.session.client.exception.EngineRejectedException;
import com.flamingo.tictactoe.session.client.exception.EngineUnavailableException;
import com.flamingo.tictactoe.session.domain.Mark;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * The session service's view of the engine: session-domain types in, session-domain types out,
 * with HTTP and its failure modes handled here rather than leaking into the simulation loop.
 *
 * <p>Retry and circuit-breaker policy live on these methods. The retry is configured to fire on
 * {@link EngineUnavailableException} alone, so a rejected move can never be retried by accident —
 * the classification happens once, in {@link #translate}, instead of being restated wherever a
 * call is made.
 */
@Component
public class GameEngineGateway {

    private static final String INSTANCE = "gameEngine";

    private static final Logger log = LoggerFactory.getLogger(GameEngineGateway.class);

    private final GameEngineClient client;
    private final EngineStateMapper mapper;

    public GameEngineGateway(GameEngineClient client, EngineStateMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    @Retry(name = INSTANCE)
    @CircuitBreaker(name = INSTANCE)
    public EngineGameState createGame(String gameId, Mark startingPlayer) {
        return call(() -> client.createGame(new CreateGameCommand(gameId, startingPlayer.name())),
                "create game " + gameId);
    }

    @Retry(name = INSTANCE)
    @CircuitBreaker(name = INSTANCE)
    public EngineGameState getGame(String gameId) {
        return call(() -> client.getGame(gameId), "read game " + gameId);
    }

    @Retry(name = INSTANCE)
    @CircuitBreaker(name = INSTANCE)
    public EngineGameState applyMove(String gameId, Mark player, int position, Long expectedVersion) {
        return call(() -> client.move(gameId, new PlayMoveCommand(player.name(), position, expectedVersion)),
                "play %s at %d in game %s".formatted(player, position, gameId));
    }

    private EngineGameState call(EngineCall call, String description) {
        try {
            return mapper.toDomain(call.execute());
        } catch (RestClientResponseException | ResourceAccessException failure) {
            throw translate(failure, description);
        }
    }

    /**
     * Sorts a failure into "try again" or "the engine said no".
     *
     * <p>4xx means the engine considered the request and refused it; the answer will not change
     * on a second attempt. Everything else — 5xx, a refused connection, a timeout — could be
     * transient, so it becomes the retryable type.
     */
    private EngineException translate(RuntimeException failure, String description) {
        if (failure instanceof RestClientResponseException response) {
            int status = response.getStatusCode().value();
            if (status >= 400 && status < 500) {
                String code = errorCodeOf(response);
                log.debug("Engine refused to {}: {} {}", description, status, code);
                return new EngineRejectedException(status, code,
                        "Engine refused to %s: %d %s".formatted(description, status, code));
            }
            return new EngineUnavailableException(
                    "Engine failed to %s: HTTP %d".formatted(description, status), failure);
        }
        return new EngineUnavailableException(
                "Engine unreachable while trying to %s".formatted(description), failure);
    }

    /** Pulls the engine's {@code code} out of its RFC-7807 body, falling back to the status. */
    private static String errorCodeOf(RestClientResponseException response) {
        try {
            var problem = response.getResponseBodyAs(java.util.Map.class);
            Object code = problem == null ? null : problem.get("code");
            return code == null ? "HTTP_" + response.getStatusCode().value() : code.toString();
        } catch (RuntimeException unparseable) {
            return "HTTP_" + response.getStatusCode().value();
        }
    }

    @FunctionalInterface
    private interface EngineCall {
        EngineGameStateResponse execute();
    }
}
