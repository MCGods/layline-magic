package com.example.laylines.api;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
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
     * The one attribute this node generates, or {@code null} if it is a
     * transmission-only / sinkhole node (the 50% generation rule).
     */
    private AttributeType generatingType;

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
        this.generatingType = rollGeneratingType(new Random());
        initAttributePools();
    }

    /**
     * Full constructor for later loaders / tests: skip the random roll and
     * supply the generating type directly ({@code null} = non-generating).
     */
    public LaylineNode(UUID id, BlockPos position, AttributeType generatingType) {
        this.id = id;
        this.position = position;
        this.generatingType = generatingType;
        initAttributePools();
    }

    /**
     * Full constructor for later loaders / tests: skip the random roll and
     * supply the generating type directly ({@code null} = non-generating).
     */
    private LaylineNode(UUID id, BlockPos position, AttributeType generatingType,
            EnumMap<AttributeType, AttributePool> pools) {
        this.id = id;
        this.position = position;
        this.generatingType = generatingType;
        this.attributes.putAll(pools);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(id);
        buffer.writeBlockPos(position);
        if (generatingType != null) {
            buffer.writeBoolean(true);
            buffer.writeEnum(generatingType);
        } else {
            buffer.writeBoolean(false);
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
        AttributeType genType = buffer.readBoolean() ? buffer.readEnum(AttributeType.class) : null;
        EnumMap<AttributeType, AttributePool> pools = new EnumMap<>(AttributeType.class);
        for (int i = 0; i < AttributeType.values().length; i++) {
            AttributeType typeName = AttributeType.valueOf(buffer.readUtf());
            float capacity = buffer.readFloat();
            float currentValue = buffer.readFloat();
            pools.put(typeName, new AttributePool(capacity, currentValue));

        }

        return new LaylineNode(id, position, genType, pools);
    }

    /**
     * 50% chance of {@code null} (non-generating). Otherwise one of the six
     * types is chosen uniformly.
     */
    private static AttributeType rollGeneratingType(Random random) {
        if (random.nextBoolean()) {
            return null;
        }
        AttributeType[] types = AttributeType.values();
        return types[random.nextInt(types.length)];
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
     * Only the matching {@link #generatingType} regenerates; everything else is 0.
     */
    public float getGenerationRate(AttributeType type) {
        if (type != null && type == generatingType) {
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
     * {@code null} means this node does not generate any attribute.
     */
    public AttributeType getGeneratingType() {
        return generatingType;
    }

    public void setGeneratingType(AttributeType generatingType) {
        this.generatingType = generatingType;
    }

    public boolean isGenerating() {
        return generatingType != null;
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
    public String toString() {
        return "LaylineNode{id=" + id
                + ", pos=" + position
                + ", generating=" + generatingType
                + "}";
    }
}
