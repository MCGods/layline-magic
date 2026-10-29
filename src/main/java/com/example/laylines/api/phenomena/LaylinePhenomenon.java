package com.example.laylines.api.phenomena;

import com.example.laylines.api.LaylineEdge;
import com.example.laylines.api.LaylineNode;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/**
 * World-targeted effect that triggers along energized layline edges on server ticks.
 *
 * External mods can extend this class to add new phenomena to the Laylines network.
 */
public abstract class LaylinePhenomenon implements ILaylineEffect {

    private final ResourceLocation id;
    private final String name;
    private final int priority;

    public LaylinePhenomenon(ResourceLocation id, String name, int priority) {
        this.id = id;
        this.name = name;
        this.priority = priority;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public boolean isEnabled() {
        return PhenomenaRegistry.getRule(this).enabled();
    }

    /**
     * Default rule configuration for this phenomenon when not overridden by config.
     */
    public abstract PhenomenaRule defaultRule();

    /**
     * Checks environmental / world conditions for this phenomenon on the edge.
     * For example, a CursedNight phenomenon might check {@code !level.isDay()}.
     *
     * @param level The server level
     * @param edge  The layline edge
     * @param rule  The active configuration rule
     * @return true if conditions permit execution
     */
    public abstract boolean canApply(ServerLevel level, LaylineEdge edge, PhenomenaRule rule);

    /**
     * Executes the phenomenon along the edge.
     *
     * @param level      The server level
     * @param edge       The layline edge
     * @param rule       The active configuration rule
     * @param sourceNode The source node providing mana
     * @return The number of targets affected (used to deduct per-target mana). Return 0 if no action occurred.
     */
    public abstract int execute(ServerLevel level, LaylineEdge edge, PhenomenaRule rule, LaylineNode sourceNode);
}

