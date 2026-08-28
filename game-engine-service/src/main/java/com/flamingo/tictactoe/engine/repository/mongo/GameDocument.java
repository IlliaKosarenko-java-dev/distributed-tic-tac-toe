package com.flamingo.tictactoe.engine.repository.mongo;

import com.flamingo.tictactoe.engine.domain.GameStatus;
import com.flamingo.tictactoe.engine.domain.Player;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 *
 * <p>A whole game is one document, which is what lets a move be an atomic compare-and-swap:
 * MongoDB guarantees single-document atomicity, so the {@link Version} check holds across
 * engine replicas rather than merely within one JVM.
 *
 */
@Document(collection = "games")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class GameDocument {

    @Id
    private String id;

    /** Nine cells in reading order; null means free. */
    private List<Player> board;

    private Player nextPlayer;

    private GameStatus status;

    private int moveCount;

    private List<Integer> winningLine;

    private Player winner;

    @Version
    private Long version;

    private Instant createdAt;

    private Instant updatedAt;
}
