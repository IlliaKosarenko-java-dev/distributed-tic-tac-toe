package com.flamingo.tictactoe.session.service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Captures published events so a test can assert what a watcher would have seen. */
public class RecordingEventPublisher implements SessionEventPublisher {

    private final List<SessionEvent> events = new CopyOnWriteArrayList<>();
    private final List<UUID> closedStreams = new CopyOnWriteArrayList<>();

    @Override
    public void publish(SessionEvent event) {
        events.add(event);
    }

    @Override
    public void closeStream(UUID sessionId) {
        closedStreams.add(sessionId);
    }

    public List<SessionEvent> events() {
        return List.copyOf(events);
    }

    public List<String> eventNames() {
        return events.stream().map(SessionEvent::name).toList();
    }

    public List<UUID> closedStreams() {
        return List.copyOf(closedStreams);
    }

    public <T extends SessionEvent> List<T> ofType(Class<T> type) {
        return events.stream().filter(type::isInstance).map(type::cast).toList();
    }
}
