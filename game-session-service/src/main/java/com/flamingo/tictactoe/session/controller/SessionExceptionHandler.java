package com.flamingo.tictactoe.session.controller;

import com.flamingo.tictactoe.session.client.exception.EngineRejectedException;
import com.flamingo.tictactoe.session.client.exception.EngineUnavailableException;
import com.flamingo.tictactoe.session.service.exception.ConcurrentSessionUpdateException;
import com.flamingo.tictactoe.session.service.exception.SessionNotFoundException;
import com.flamingo.tictactoe.session.service.exception.SimulationAlreadyStartedException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.TypeMismatchException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.UUID;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;

/**
 * <p>The engine's own failures get distinct statuses on purpose: a refusal is 502 (the upstream
 * gave a definite answer this service cannot act on) while an outage is 503 with
 * {@code Retry-After} (come back later). Collapsing both into 500 would tell a caller nothing
 * about whether retrying is worth it.
 */
@RestControllerAdvice
public class SessionExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(SessionExceptionHandler.class);
    private static final String ERROR_TYPE_BASE = "https://flamingo.example/errors/";

    @ExceptionHandler(SessionNotFoundException.class)
    public ProblemDetail handleSessionNotFound(SessionNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Session not found", ex.getMessage(), "SESSION_NOT_FOUND");
    }

    @ExceptionHandler(SimulationAlreadyStartedException.class)
    public ProblemDetail handleAlreadyStarted(SimulationAlreadyStartedException ex) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Simulation already started",
                ex.getMessage(), "SIMULATION_ALREADY_STARTED");
        problem.setProperty("currentStatus", ex.currentStatus());
        return problem;
    }

    @ExceptionHandler(ConcurrentSessionUpdateException.class)
    public ProblemDetail handleConcurrentUpdate(ConcurrentSessionUpdateException ex) {
        return problem(HttpStatus.CONFLICT, "Concurrent modification", ex.getMessage(),
                "SESSION_VERSION_CONFLICT");
    }

    /** The engine answered and said no; retrying will not change that. */
    @ExceptionHandler(EngineRejectedException.class)
    public ProblemDetail handleEngineRejection(EngineRejectedException ex) {
        ProblemDetail problem = problem(HttpStatus.BAD_GATEWAY, "Engine rejected the request",
                ex.getMessage(), "ENGINE_REJECTED");
        problem.setProperty("engineCode", ex.code());
        problem.setProperty("engineStatus", ex.status());
        return problem;
    }

    @ExceptionHandler(EngineUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleEngineOutage(EngineUnavailableException ex) {
        log.warn("Engine unavailable: {}", ex.getMessage());
        return retryLater(problem(HttpStatus.SERVICE_UNAVAILABLE, "Engine unavailable",
                ex.getMessage(), "ENGINE_UNAVAILABLE"));
    }

    /** The breaker is open, so the engine is not even being asked. Same advice: come back. */
    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ProblemDetail> handleBreakerOpen(CallNotPermittedException ex) {
        return retryLater(problem(HttpStatus.SERVICE_UNAVAILABLE, "Engine unavailable",
                "The engine is not responding; requests are being shed", "ENGINE_CIRCUIT_OPEN"));
    }

    /** The simulation pool is saturated — shedding load rather than queueing indefinitely. */
    @ExceptionHandler(RejectedExecutionException.class)
    public ResponseEntity<ProblemDetail> handlePoolFull(RejectedExecutionException ex) {
        return retryLater(problem(HttpStatus.SERVICE_UNAVAILABLE, "Too many simulations",
                "No capacity to start another simulation right now", "SIMULATION_CAPACITY"));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(
                        error.getField(), error.getDefaultMessage(), error.getRejectedValue()))
                .sorted(Comparator.comparing(FieldViolation::field))
                .toList();

        String detail = violations.stream()
                .map(violation -> "%s %s".formatted(violation.field(), violation.message()))
                .collect(Collectors.joining("; "));

        ProblemDetail problem =
                problem(HttpStatus.BAD_REQUEST, "Validation failed", detail, "VALIDATION_FAILED");
        problem.setProperty("errors", violations);

        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    /**
     * A path variable that will not convert — almost always a session id that is not a UUID.
     * Binding fails before any handler method runs, so this needs its own mapping or the
     * caller gets a bare 400 with no clue which value was wrong.
     */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        String name = ex instanceof MethodArgumentTypeMismatchException mismatch
                ? mismatch.getName() : "parameter";
        boolean expectingUuid = ex.getRequiredType() != null
                && UUID.class.isAssignableFrom(ex.getRequiredType());

        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST,
                expectingUuid ? "Invalid identifier" : "Invalid parameter",
                "%s '%s' is not %s".formatted(name, ex.getValue(),
                        expectingUuid ? "a valid UUID" : "valid"),
                expectingUuid ? "INVALID_UUID" : "INVALID_PARAMETER");
        problem.setProperty("parameter", name);

        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    /** A parameter this service parses itself, e.g. an unrecognised simulate mode. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadParameter(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid parameter", ex.getMessage(), "INVALID_PARAMETER");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled failure serving request", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error",
                "The request could not be completed", "INTERNAL_ERROR");
    }

    private static ResponseEntity<ProblemDetail> retryLater(ProblemDetail problem) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "5")
                .body(problem);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(ERROR_TYPE_BASE + code.toLowerCase().replace('_', '-')));
        problem.setProperty("code", code);
        return problem;
    }

    public record FieldViolation(String field, String message, Object rejectedValue) {
    }
}
