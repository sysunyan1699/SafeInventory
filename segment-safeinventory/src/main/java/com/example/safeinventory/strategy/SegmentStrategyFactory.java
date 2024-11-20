package com.example.safeinventory.strategy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 分段选择策略工厂
 */
@Component
public class SegmentStrategyFactory {

    @Autowired
    private BestMatchSegmentStrategy bestMatchStrategy;

    @Autowired
    private SequentialSegmentStrategy sequentialStrategy;

    public enum StrategyType {
        BEST_MATCH("best_match"),
        SEQUENTIAL("sequential");

        private final String value;

        StrategyType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * 获取策略实例
     */
    public SegmentSelectionStrategy getStrategy(StrategyType type) {
        switch (type) {
            case BEST_MATCH:
                return bestMatchStrategy;
            case SEQUENTIAL:
                return sequentialStrategy;
            default:
                throw new IllegalArgumentException("Unknown strategy type: " + type);
        }
    }
} 