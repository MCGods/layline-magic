package com.example.laylines.api.phenomena;

import com.example.laylines.api.LaylineNode;
import com.example.laylines.api.LaylineNode.AttributeType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Immutable configuration rule for a layline phenomenon or effect.
 *
 * @param cost     Per-target mana cost required from the source node, mapped by attribute type.
 * @param range    Maximum horizontal distance in blocks from the layline edge.
 * @param chance   Probability (0.0 to 1.0) of this phenomenon activating on an affordable edge.
 * @param strength Maximum number of targets affected per activation (e.g. crops grown, entities affected).
 */
public record PhenomenaRule(
        Map<AttributeType, Float> cost,
        double range,
        double chance,
        int strength
) {
    public PhenomenaRule {
        Map<AttributeType, Float> copy = new EnumMap<>(AttributeType.class);
        if (cost != null) {
            copy.putAll(cost);
        }
        cost = Collections.unmodifiableMap(copy);
    }

    /**
     * Gets the mana cost for a specific attribute type, returning 0 if not required.
     */
    public float getCost(AttributeType type) {
        return cost.getOrDefault(type, 0.0f);
    }

    /**
     * Checks if this rule has no mana costs.
     */
    public boolean isFree() {
        return cost.isEmpty();
    }

    /**
     * Calculates the maximum number of targets that can be afforded by the given source node,
     * capped at {@link #strength()}.
     */
    public int calculateMaxAffordable(LaylineNode sourceNode) {
        if (isFree()) {
            return strength;
        }

        int maxAffordable = strength;
        for (Map.Entry<AttributeType, Float> entry : cost.entrySet()) {
            float costPerTarget = entry.getValue();
            if (costPerTarget <= 0.0f) {
                continue;
            }
            float available = sourceNode.getCurrentValue(entry.getKey());
            int affordableForAttr = (int) Math.floor(available / costPerTarget);
            maxAffordable = Math.min(maxAffordable, affordableForAttr);
        }

        return Math.max(0, maxAffordable);
    }
}

