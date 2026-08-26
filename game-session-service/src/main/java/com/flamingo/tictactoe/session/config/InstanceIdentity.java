package com.flamingo.tictactoe.session.config;

import java.util.UUID;

/**
 * Identifies this process to the rest of the system.
 *
 * <p>Stamped onto a session when a runner claims it, so a session left RUNNING can be traced
 * back to the instance that was driving it when it stopped. With one replica this is merely a
 * useful log line; with several it is the only way to tell which of them owns a given game.
 *
 * @param id short, human-readable, and unique per process
 */
public record InstanceIdentity(String id) {

    public static InstanceIdentity generate() {
        return new InstanceIdentity("instance-" + UUID.randomUUID().toString().substring(0, 8));
    }
}
