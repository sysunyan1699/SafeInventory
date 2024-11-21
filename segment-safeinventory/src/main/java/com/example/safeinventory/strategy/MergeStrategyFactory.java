package com.example.safeinventory.strategy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MergeStrategyFactory {
    
    @Autowired
    private UsageRatioMergeStrategy usageRatioStrategy;

    public enum MergeStrategyType {
        USAGE_RATIO("usage_ratio");

        private final String value;

        MergeStrategyType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public MergeCheckStrategy getStrategy(MergeStrategyType type) {
        switch (type) {
            case USAGE_RATIO:
                return usageRatioStrategy;
            default:
                throw new IllegalArgumentException("Unknown merge strategy type: " + type);
        }
    }
} 