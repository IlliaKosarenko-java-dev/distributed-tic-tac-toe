package com.flamingo.tictactoe.session.service;

/**
 * Where simulation progress goes.
 *
 * <p>An interface so the runner never touches {@code SseEmitter}: the transport belongs to the
 * web layer, and a service that imported it could not be tested without one. It also leaves room
 * for a second implementation — a change-stream fan-out for multiple replicas — without the loop
 * changing at all.
 */
public interface SessionEventPublisher {

    void publish(SessionEvent event);

    /** No watcher will receive anything further for this session. */
    void closeStream(String sessionId);
}
