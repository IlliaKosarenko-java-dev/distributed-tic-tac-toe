package com.flamingo.tictactoe.engine.controller;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.flamingo.tictactoe.engine.service.exception.ConcurrentGameUpdateException;
import com.flamingo.tictactoe.engine.service.exception.GameNotFoundException;
import com.flamingo.tictactoe.engine.domain.Player;
import com.flamingo.tictactoe.engine.domain.exception.InvalidPositionException;
import com.flamingo.tictactoe.engine.domain.exception.MoveRejectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Maps failures onto RFC-7807 responses.
 *
 * <p>Every response carries a machine-readable {@code code} alongside the status, because the
 * session service has to tell a permanent verdict (do not retry) from a transient one (safe to
 * retry), and 409 alone does not say which it is.
 *
 * <p>Extends {@link ResponseEntityExceptionHandler} so the framework's own exceptions — unknown
 * method, unsupported media type, unreadable body — keep their correct statuses instead of
 * being swallowed by the catch-all below. Boot's built-in problem-detail advice backs off in
 * the presence of this bean, so there is exactly one advice in play.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String ERROR_TYPE_BASE = "https://flamingo.example/errors/";

    @ExceptionHandler(GameNotFoundException.class)
    public ProblemDetail handleGameNotFound(GameNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Game not found", ex.getMessage(), "GAME_NOT_FOUND");
    }

    /**
     * An illegal move. Deterministic: the same request will be refused again, so the code
     * tells the caller not to retry.
     */
    @ExceptionHandler(MoveRejectedException.class)
    public ProblemDetail handleMoveRejected(MoveRejectedException ex) {
        return problem(HttpStatus.CONFLICT, "Move rejected", ex.getMessage(), ex.reason().name());
    }

    /**
     * Lost a race rather than broke a rule — re-reading and replaying may well succeed.
     */
    @ExceptionHandler(ConcurrentGameUpdateException.class)
    public ProblemDetail handleConcurrentUpdate(ConcurrentGameUpdateException ex) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Concurrent modification",
                ex.getMessage(), "VERSION_CONFLICT");
        problem.setProperty("expectedVersion", ex.expectedVersion());
        return problem;
    }

    /**
     * Defence in depth: bean validation bounds the position before a request reaches the
     * domain, so this fires only for a caller that bypasses the DTO.
     */
    @ExceptionHandler(InvalidPositionException.class)
    public ProblemDetail handleInvalidPosition(InvalidPositionException ex) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Invalid position",
                ex.getMessage(), "POSITION_OUT_OF_RANGE");
        problem.setProperty("position", ex.index());
        return problem;
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
        // Field-level detail as well as prose, so a caller can react per field instead of
        // parsing the message.
        problem.setProperty("errors", violations);

        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    /**
     * Unparseable body. An unknown player symbol lands here rather than in bean validation,
     * because Jackson fails before the object exists — worth its own code so the caller can
     * see that it sent "Z" rather than a vague parse error.
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        InvalidFormatException badValue = findInvalidFormat(ex);
        ProblemDetail problem = (badValue != null && badValue.getTargetType() != null
                && Player.class.isAssignableFrom(badValue.getTargetType()))
                ? problem(HttpStatus.BAD_REQUEST, "Invalid player",
                        "'%s' is not a player; expected X or O".formatted(badValue.getValue()),
                        "INVALID_PLAYER")
                : problem(HttpStatus.BAD_REQUEST, "Malformed request",
                        "Request body could not be parsed", "MALFORMED_REQUEST");

        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        // Logged at error with the stack trace; the response deliberately says no more than
        // that it failed, so internals are not leaked to callers.
        log.error("Unhandled failure serving request", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error",
                "The request could not be completed", "INTERNAL_ERROR");
    }

    /** Jackson may nest the offending value a few frames down, so walk the cause chain. */
    private static InvalidFormatException findInvalidFormat(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof InvalidFormatException invalidFormat) {
                return invalidFormat;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return null;
    }

    /** One failed constraint, echoed back with what was rejected. */
    public record FieldViolation(String field, String message, Object rejectedValue) {
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(ERROR_TYPE_BASE + code.toLowerCase().replace('_', '-')));
        problem.setProperty("code", code);
        return problem;
    }
}
