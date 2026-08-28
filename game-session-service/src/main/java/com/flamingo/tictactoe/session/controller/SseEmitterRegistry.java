package com.flamingo.tictactoe.session.controller;

import com.flamingo.tictactoe.session.service.SessionEvent;
import com.flamingo.tictactoe.session.service.SessionEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Holds the open SSE connections for each session and pushes events into them.
 *
 * <p>In-process by design: the simulation runs in the same JVM as the emitters, so delivery is a
 * direct call with no broker in the path. That works because a session is claimed by exactly one
 * instance. With several replicas a browser could attach to one while another drives the game,
 * and this is the seam where a MongoDB change-stream fan-out would replace the direct call —
 * the runner would not change, because it only knows {@link SessionEventPublisher}.
 *
 * <p>Every removal path is wired up: completion, timeout, error, and a write that fails because
 * the client walked away. A registry that only cleans up on the happy path leaks emitters.
 */
@Component
public class SseEmitterRegistry implements SessionEventPublisher {

    private static final Duration STREAM_TIMEOUT = Duration.ofMinutes(10);

    private static final Logger log = LoggerFactory.getLogger(SseEmitterRegistry.class);

    private final Map<UUID, List<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID sessionId) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT.toMillis());

        subscribers.computeIfAbsent(sessionId, id -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> remove(sessionId, emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            remove(sessionId, emitter);
        });
        emitter.onError(throwable -> remove(sessionId, emitter));

        log.debug("Subscriber attached to session {} ({} total)", sessionId, countFor(sessionId));
        return emitter;
    }

    @Override
    public void publish(SessionEvent event) {
        List<SseEmitter> emitters = subscribers.get(event.sessionId());
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(event.name()).data(event));
            } catch (IOException | IllegalStateException gone) {
                // The client disconnected; nothing to recover, just stop tracking it.
                remove(event.sessionId(), emitter);
            }
        }
    }

    @Override
    public void closeStream(UUID sessionId) {
        List<SseEmitter> emitters = subscribers.remove(sessionId);
        if (emitters == null) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.complete();
            } catch (RuntimeException alreadyGone) {
                log.trace("Emitter for session {} was already closed", sessionId);
            }
        }
    }

    int countFor(UUID sessionId) {
        return subscribers.getOrDefault(sessionId, List.of()).size();
    }

    private void remove(UUID sessionId, SseEmitter emitter) {
        List<SseEmitter> emitters = subscribers.get(sessionId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        // Do not leave an empty list behind keyed by a session nobody is watching.
        subscribers.computeIfPresent(sessionId, (id, current) -> current.isEmpty() ? null : current);
    }
}
