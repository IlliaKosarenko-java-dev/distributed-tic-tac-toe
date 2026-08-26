package com.flamingo.tictactoe.session.service.strategy;

import com.flamingo.tictactoe.session.domain.StrategyType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves a {@link StrategyType} to its implementation.
 *
 * <p>Built from whatever {@link MoveStrategy} beans exist, so adding a strategy is a matter of
 * writing the class — nothing here needs editing. The completeness check at construction turns
 * a forgotten registration into a startup failure rather than a mid-game one.
 */
@Component
public class MoveStrategies {

    private final Map<StrategyType, MoveStrategy> byType = new EnumMap<>(StrategyType.class);

    public MoveStrategies(List<MoveStrategy> strategies) {
        for (MoveStrategy strategy : strategies) {
            MoveStrategy clash = byType.put(strategy.type(), strategy);
            if (clash != null) {
                throw new IllegalStateException(
                        "Two strategies claim %s: %s and %s".formatted(
                                strategy.type(), clash.getClass().getSimpleName(),
                                strategy.getClass().getSimpleName()));
            }
        }
        for (StrategyType type : StrategyType.values()) {
            if (!byType.containsKey(type)) {
                throw new IllegalStateException("No MoveStrategy registered for " + type);
            }
        }
    }

    public MoveStrategy of(StrategyType type) {
        MoveStrategy strategy = byType.get(type);
        if (strategy == null) {
            throw new IllegalArgumentException("Unknown strategy " + type);
        }
        return strategy;
    }
}
