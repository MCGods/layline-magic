package com.example.laylines.api.phenomena;

import net.minecraft.resources.ResourceLocation;

/**
 * Base contract for all layline-powered magical effects.
 *
 * This serves as the common foundation for:
 * - {@link LaylinePhenomenon}: World-targeted effects acting along edges on server ticks.
 * - LaylineMiracles (future): Player-targeted effects acting on player events.
 */
public interface ILaylineEffect {

    /**
     * Unique identifier for this effect, e.g. "laylines:miracle_growth".
     */
    ResourceLocation getId();

    /**
     * Human-readable name for logging and UI.
     */
    String getName();

    /**
     * Evaluation priority. Higher numbers evaluate earlier.
     */
    int getPriority();

    /**
     * Checks if this effect is enabled (can be toggled by config).
     */
    boolean isEnabled();
}

