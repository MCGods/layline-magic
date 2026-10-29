package com.example.laylines.api;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * One vertex in the 2D layline web (README Section 2).
 *
 * Layout math lives on a horizontal plane (default {@code Y=200}). Every node
 * stores all six elemental pools independently so a Fire generator can still
 * transmit Water/Air from its neighbors without sharing a single tank.
 *
 * This class is in-memory only for now — persistence and Delaunay placement
 * come later.
 */
public class LaylineNode {

    /** Default height of the 2D layout plane. */
    public static final int DEFAULT_Y = 200;

    /**
     * Regeneration applied each tick to the node's single generating attribute.
     * Non-generating attributes (and fully non-generating nodes) use {@code 0}.
     */
    public static final float BASE_REGENERATION_RATE = 1.0f;

    /** Starting tank size for every attribute until world-gen/config tunes it. */
    public static final float DEFAULT_CAPACITY = 100.0f;

    /**
     * The six elemental channels. Every node has a pool for each of these,
     * regardless of which (if any) it actually generates.
     */
    public enum AttributeType {
        FIRE,
        WATER,
        EARTH,
        AIR,
        DARK,
        LIGHT
    }

    /**
     * Independent energy tank for one {@link AttributeType}.
     * Capacity and current value never mix with other attributes on this node.
     */
    public static class AttributePool {
        private float capacity;
        private float currentValue;

        public AttributePool(float capacity, float currentValue) {
            this.capacity = capacity;
            this.currentValue = currentValue;
        }

        public float getCapacity() {
            return capacity;
        }

        public void setCapacity(float capacity) {
            this.capacity = capacity;
        }

        public float getCurrentValue() {
            return currentValue;
        }

        public void setCurrentValue(float currentValue) {
            this.currentValue = currentValue;
        }
    }

    private final UUID id;
    private BlockPos position;

    /**
     * Set of attributes this node generates. Can contain 0 to multiple attributes.
     */
    private final Set<AttributeType> generatingTypes = EnumSet.noneOf(AttributeType.class);

    private final EnumMap<AttributeType, AttributePool> attributes = new EnumMap<>(AttributeType.class);

    /**
     * Creates a node at the origin of the layout plane ({@code 0, 200, 0})
     * with a fresh UUID and a random generating type (50% chance of none).
     */
    public LaylineNode() {
        this(UUID.randomUUID(), new BlockPos(0, DEFAULT_Y, 0));
    }

    /**
     * Creates a node at {@code position} (Y is left as-is so structure anchors
     * can sit at their real height later). Generating type is rolled randomly.
     */
    public LaylineNode(BlockPos position) {
        this(UUID.randomUUID(), position);
    }

    /**
     * Creates a node at world X/Z on the default layout plane ({@code Y=200}).
     */
    public LaylineNode(int x, int z) {
        this(UUID.randomUUID(), new BlockPos(x, DEFAULT_Y, z));
    }

    public LaylineNode(UUID id, BlockPos position) {
        this.id = id;
        this.position = position;
        initAttributePools();
    }

    /**
     * Full constructor supporting multiple generating attributes.
     */
    public LaylineNode(UUID id, BlockPos position, Collection<AttributeType> generatingTypes) {
        this.id = id;
        this.position = position;
        if (generatingTypes != null) {
            this.generatingTypes.addAll(generatingTypes);
        }
        initAttributePools();
    }

    /**
     * Full constructor for decoding / loaders.
     */
    public LaylineNode(UUID id, BlockPos position, Collection<AttributeType> generatingTypes,
            EnumMap<AttributeType, AttributePool> pools) {
        this.id = id;
        this.position = position;
        if (generatingTypes != null) {
            this.generatingTypes.addAll(generatingTypes);
        }
        this.attributes.putAll(pools);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(id);
        buffer.writeBlockPos(position);
        buffer.writeVarInt(generatingTypes.size());
        for (AttributeType type : generatingTypes) {
            buffer.writeEnum(type);
        }
        for (AttributeType type : AttributeType.values()) {
            AttributePool pool = attributes.get(type);
            buffer.writeUtf(type.toString());
            buffer.writeFloat(pool.getCapacity());
            buffer.writeFloat(pool.getCurrentValue());
        }
    }

    public static LaylineNode decode(FriendlyByteBuf buffer) {
        UUID id = buffer.readUUID();
        BlockPos position = buffer.readBlockPos();
        int genCount = buffer.readVarInt();
        Set<AttributeType> genTypes = EnumSet.noneOf(AttributeType.class);
        for (int i = 0; i < genCount; i++) {
            genTypes.add(buffer.readEnum(AttributeType.class));
        }
        EnumMap<AttributeType, AttributePool> pools = new EnumMap<>(AttributeType.class);
        for (int i = 0; i < AttributeType.values().length; i++) {
            AttributeType typeName = AttributeType.valueOf(buffer.readUtf());
            float capacity = buffer.readFloat();
            float currentValue = buffer.readFloat();
            pools.put(typeName, new AttributePool(capacity, currentValue));
        }

        return new LaylineNode(id, position, genTypes, pools);
    }

    private void initAttributePools() {
        for (AttributeType type : AttributeType.values()) {
            attributes.put(type, new AttributePool(DEFAULT_CAPACITY, 0.0f));
        }
    }

    /**
     * Set the capacity for a specific attribute pool.
     */
    public void setCapacity(AttributeType type, float capacity) {
        AttributePool pool = attributes.get(type);
        if (pool != null) {
            pool.setCapacity(capacity);
        }
    }

    /**
     * Rate this node currently generates for {@code type}.
     * Only matching generating attributes regenerate; everything else is 0.
     */
    public float getGenerationRate(AttributeType type) {
        if (type != null && generatingTypes.contains(type)) {
            return BASE_REGENERATION_RATE;
        }
        return 0.0f;
    }

    public UUID getId() {
        return id;
    }

    public BlockPos getPosition() {
        return position;
    }

    public void setPosition(BlockPos position) {
        this.position = position;
    }

    /**
     * Returns an unmodifiable set of all attributes generated by this node.
     */
    public Set<AttributeType> getGeneratingTypes() {
        return Collections.unmodifiableSet(generatingTypes);
    }

    /**
     * Sets the attributes generated by this node.
     */
    public void setGeneratingTypes(Collection<AttributeType> types) {
        this.generatingTypes.clear();
        if (types != null) {
            this.generatingTypes.addAll(types);
        }
    }

    /**
     * Checks if a specific attribute is generated by this node.
     */
    public boolean isGenerating(AttributeType type) {
        return type != null && generatingTypes.contains(type);
    }

    /**
     * Checks if this node generates any attribute.
     */
    public boolean isGenerating() {
        return !generatingTypes.isEmpty();
    }

    public AttributePool getAttribute(AttributeType type) {
        return attributes.get(type);
    }

    public float getCapacity(AttributeType type) {
        return attributes.get(type).getCapacity();
    }

    public float getCurrentValue(AttributeType type) {
        return attributes.get(type).getCurrentValue();
    }

    public void setCurrentValue(AttributeType type, float currentValue) {
        attributes.get(type).setCurrentValue(currentValue);
    }

    /**
     * Unmodifiable view of all six pools. Safe to iterate; mutate via setters.
     */
    public Map<AttributeType, AttributePool> getAttributes() {
        return Map.copyOf(attributes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        LaylineNode that = (LaylineNode) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "LaylineNode{id=" + id
                + ", pos=" + position
                + ", generating=" + generatingTypes
                + "}";
    }
}
