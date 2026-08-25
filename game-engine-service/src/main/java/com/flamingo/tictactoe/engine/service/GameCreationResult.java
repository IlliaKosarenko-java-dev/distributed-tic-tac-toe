package com.flamingo.tictactoe.engine.service;

import com.flamingo.tictactoe.engine.repository.StoredGame;

/**
 * @param game    the stored game, newly created or pre-existing
 * @param created true only when this call is the one that created it
 */
public record GameCreationResult(StoredGame game, boolean created) {
}
