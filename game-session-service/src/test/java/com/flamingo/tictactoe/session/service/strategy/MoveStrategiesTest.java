package com.flamingo.tictactoe.session.service.strategy;

import com.flamingo.tictactoe.session.domain.BoardSnapshot;
import com.flamingo.tictactoe.session.domain.Mark;
import com.flamingo.tictactoe.session.domain.StrategyType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoveStrategiesTest {

    private static final MoveStrategy RANDOM = new RandomMoveStrategy(new Random(1));
    private static final MoveStrategy RULE_BASED = new RuleBasedMoveStrategy();

    @Test
    void resolvesEachTypeToItsImplementation() {
        MoveStrategies strategies = new MoveStrategies(List.of(RANDOM, RULE_BASED));

        assertThat(strategies.of(StrategyType.RANDOM)).isSameAs(RANDOM);
        assertThat(strategies.of(StrategyType.RULE_BASED)).isSameAs(RULE_BASED);
    }

    @Test
    void refusesToStartWhenAStrategyIsMissing() {
        assertThatThrownBy(() -> new MoveStrategies(List.of(RANDOM)))
                .as("a missing strategy should fail at startup, not mid-game")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RULE_BASED");
    }

    @Test
    void refusesTwoStrategiesClaimingTheSameType() {
        MoveStrategy duplicate = new MoveStrategy() {
            @Override
            public StrategyType type() {
                return StrategyType.RANDOM;
            }

            @Override
            public int chooseMove(BoardSnapshot board, Mark player) {
                return 0;
            }
        };

        assertThatThrownBy(() -> new MoveStrategies(List.of(RANDOM, RULE_BASED, duplicate)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("claim");
    }
}
