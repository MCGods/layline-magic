package com.example.laylines.api;

import net.minecraft.core.BlockPos;

import java.util.Objects;

import com.example.laylines.api.LaylineNode.AttributeType;

/**
 * Server-side representation of an edge in the layline network.
 * Stores complete LaylineNode instances for source and target endpoints.
 * Direction of flow is from source to target.
 * Edge metadata is derived directly from the source node.
 */
public record LaylineEdge(LaylineNode source, LaylineNode target) {

    public LaylineEdge {
        Objects.requireNonNull(source, "Source node cannot be null");
        Objects.requireNonNull(target, "Target node cannot be null");
    }

    public BlockPos getSourcePos() {
        return source.getPosition();
    }

    public BlockPos getTargetPos() {
        return target.getPosition();
    }

    /**
     * Backward-compatible alias for geometric operations (nodeA = source position).
     */
    public BlockPos nodeA() {
        return source.getPosition();
    }

    /**
     * Backward-compatible alias for geometric operations (nodeB = target position).
     */
    public BlockPos nodeB() {
        return target.getPosition();
    }

    /**
     * Edge metadata is that of the source node.
     */
    public AttributeType getGeneratingType() {
        return source.getGeneratingType();
    }

    /**
     * Energy available from the source node for this attribute.
     */
    public float getCurrentEnergy(AttributeType type) {
        return type == null ? 0.0f : source.getCurrentValue(type);
    }
}
