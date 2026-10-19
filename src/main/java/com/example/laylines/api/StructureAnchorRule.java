package com.example.laylines.api;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.Map;
import java.util.Objects;

import com.example.laylines.api.LaylineNode.AttributeType;

/**
 * Rule describing which structure can act as an anchor for a layline node.
 *
 * <p>
 * Each rule contains:
 * <ul>
 * <li>a {@link ResourceKey} identifying the vanilla (or mod) structure</li>
 * <li>a {@code priority} – higher values win when multiple structures intersect
 * a cell</li>
 * <li>a map of {@link AttributeType} → generation weight (odds). The sum of all
 * weights is used
 * for a deterministic weighted random pick.</li>
 * <li>a map of {@link AttributeType} → {@link IntRange} defining the capacity
 * range for the various attributes. If a type is missing, the default range is
 * used.</li>
 * </ul>
 */
public record StructureAnchorRule(
        ResourceKey<Structure> structureKey,
        int priority,
        Map<AttributeType, Integer> generationOdds,
        Map<AttributeType, IntRange> capacityRanges) {

    public StructureAnchorRule {
        Objects.requireNonNull(structureKey, "structureKey cannot be null");
        Objects.requireNonNull(generationOdds, "generationOdds cannot be null");
        Objects.requireNonNull(capacityRanges, "capacityRanges cannot be null");
        if (priority < 0)
            throw new IllegalArgumentException("priority must be non‑negative");
        if (generationOdds.isEmpty())
            throw new IllegalArgumentException("generationOdds cannot be empty");
    }

    /** Helper to create a {@link ResourceKey} from a plain namespaced id string */
    public static ResourceKey<Structure> keyFromId(String id) {
        return ResourceKey.create(Registries.STRUCTURE, new ResourceLocation(id));
    }
}
