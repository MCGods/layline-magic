package com.example.laylines.saveddata;

import com.example.laylines.network.LaylineNode;
import com.example.laylines.network.LaylineNode.AttributeType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Global overworld SavedData for the layline web.
 *
 * Minecraft writes this to {@code <world>/data/laylines.dat} whenever the
 * instance is marked dirty (see {@link #putNode(LaylineNode)}). Load happens
 * automatically the first time {@link #get(MinecraftServer)} or
 * {@code computeIfAbsent(factory(), DATA_NAME)} runs on the overworld.
 */
public class LaylinesData extends SavedData {

    /** File name without {@code .dat}; must match the computeIfAbsent id. */
    public static final String DATA_NAME = "laylines";

    private static final String NODES_KEY = "nodes";
    private static final String UUID_KEY = "uuid";
    private static final String X_KEY = "x";
    private static final String Y_KEY = "y";
    private static final String Z_KEY = "z";
    private static final String GENERATING_TYPE_KEY = "generatingType";
    private static final String ATTRIBUTES_KEY = "attributes";
    private static final String ATTR_TYPE_KEY = "type";
    private static final String CAPACITY_KEY = "capacity";
    private static final String CURRENT_VALUE_KEY = "currentValue";

    private final Map<UUID, LaylineNode> nodes = new HashMap<>();

    public static SavedData.Factory<LaylinesData> factory() {
        return new SavedData.Factory<>(LaylinesData::new, LaylinesData::load, DataFixTypes.LEVEL);
    }

    /**
     * Loads (or creates) the overworld copy. Call this instead of constructing
     * {@code new LaylinesData()} at runtime so disk data is reused.
     */
    public static LaylinesData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    public LaylinesData() {
    }

    public LaylinesData(Map<UUID, LaylineNode> nodes) {
        this.nodes.putAll(nodes);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookupProvider) {
        ListTag nodeList = new ListTag();

        for (LaylineNode node : nodes.values()) {
            CompoundTag nodeTag = new CompoundTag();
            nodeTag.putUUID(UUID_KEY, node.getId());

            BlockPos pos = node.getPosition();
            nodeTag.putInt(X_KEY, pos.getX());
            nodeTag.putInt(Y_KEY, pos.getY());
            nodeTag.putInt(Z_KEY, pos.getZ());

            // Empty string means a non-generating (transmission-only) node.
            AttributeType generatingType = node.getGeneratingType();
            nodeTag.putString(GENERATING_TYPE_KEY, generatingType == null ? "" : generatingType.name());

            ListTag attributeList = new ListTag();
            for (AttributeType type : AttributeType.values()) {
                CompoundTag poolTag = new CompoundTag();
                poolTag.putString(ATTR_TYPE_KEY, type.name());
                poolTag.putFloat(CAPACITY_KEY, node.getCapacity(type));
                poolTag.putFloat(CURRENT_VALUE_KEY, node.getCurrentValue(type));
                attributeList.add(poolTag);
            }
            nodeTag.put(ATTRIBUTES_KEY, attributeList);

            nodeList.add(nodeTag);
        }

        tag.put(NODES_KEY, nodeList);
        return tag;
    }

    /**
     * Rebuilds the UUID → node map from NBT written by {@link #save}.
     * Uses {@link LaylineNode}'s full constructor so generation is not re-rolled.
     */
    public static LaylinesData load(CompoundTag tag, HolderLookup.Provider lookupProvider) {
        Map<UUID, LaylineNode> loaded = new HashMap<>();
        ListTag nodeList = tag.getList(NODES_KEY, Tag.TAG_COMPOUND);

        for (int i = 0; i < nodeList.size(); i++) {
            CompoundTag nodeTag = nodeList.getCompound(i);

            UUID id = nodeTag.getUUID(UUID_KEY);
            BlockPos pos = new BlockPos(nodeTag.getInt(X_KEY), nodeTag.getInt(Y_KEY), nodeTag.getInt(Z_KEY));

            String generatingName = nodeTag.getString(GENERATING_TYPE_KEY);
            AttributeType generatingType = generatingName.isEmpty()
                    ? null
                    : AttributeType.valueOf(generatingName);

            LaylineNode node = new LaylineNode(id, pos, generatingType);

            ListTag attributeList = nodeTag.getList(ATTRIBUTES_KEY, Tag.TAG_COMPOUND);
            for (int j = 0; j < attributeList.size(); j++) {
                CompoundTag poolTag = attributeList.getCompound(j);
                AttributeType type = AttributeType.valueOf(poolTag.getString(ATTR_TYPE_KEY));
                node.setCapacity(type, poolTag.getFloat(CAPACITY_KEY));
                node.setCurrentValue(type, poolTag.getFloat(CURRENT_VALUE_KEY));
            }

            loaded.put(id, node);
        }

        return new LaylinesData(loaded);
    }

    public Map<UUID, LaylineNode> getNodes() {
        return Collections.unmodifiableMap(nodes);
    }

    public LaylineNode getNode(UUID id) {
        return nodes.get(id);
    }

    /**
     * Inserts or replaces a node and marks this SavedData dirty so Minecraft
     * will write {@code laylines.dat} on the next world save.
     */
    public void putNode(LaylineNode node) {
        nodes.put(node.getId(), node);
        setDirty();
    }

    public void removeNode(UUID id) {
        if (nodes.remove(id) != null) {
            setDirty();
        }
    }

    /** Call after mutating a node that is already in the map. */
    public void markDirty() {
        setDirty();
    }
}
