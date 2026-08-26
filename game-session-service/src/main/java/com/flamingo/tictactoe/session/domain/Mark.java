package com.flamingo.tictactoe.session.domain;

/**
 * A player symbol as this service understands it.
 *
 * <p>Deliberately its own type rather than a shared one: the engine owns the rules and this
 * service owns sessions, and coupling them through a common class would mean neither could
 * change its representation without the other's agreement. The duplication is the price of
 * that independence, and it is a small one — two constants.
 */
public enum Mark {

    X,
    O;

    public Mark opponent() {
        return this == X ? O : X;
    }
}
