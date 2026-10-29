package com.example.laylines.clientdata;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.example.laylines.api.LaylineNode;
import com.example.laylines.api.LaylineNode.AttributeType;

/**
 * Client-side Data Transfer Object (DTO) for a layline node.
 * Contains public coordinate information and raw pool levels;
 * never exposes internal cell coordinates or private server data.
 */
public record ClientLaylineNode(
        UUID id,
        BlockPos position,
        Set<AttributeType> generatingTypes,
        Map<AttributeType, Float> pools) {

    public ClientLaylineNode {
        generatingTypes = Collections.unmodifiableSet(
                generatingTypes == null || generatingTypes.isEmpty()
                        ? EnumSet.noneOf(AttributeType.class)
                        : EnumSet.copyOf(generatingTypes));
        pools = Collections.unmodifiableMap(new EnumMap<>(pools));
    }

    /**
     * Factory to construct client node data from a server LaylineNode.
     */
    public static ClientLaylineNode fromServer(LaylineNode node) {
        Map<AttributeType, Float> pools = new EnumMap<>(AttributeType.class);
        for (AttributeType type : AttributeType.values()) {
            pools.put(type, node.getCurrentValue(type));
        }
        return new ClientLaylineNode(node.getId(), node.getPosition(), node.getGeneratingTypes(), pools);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(id);
        buffer.writeBlockPos(position);
        buffer.writeVarInt(generatingTypes.size());
        for (AttributeType type : generatingTypes) {
            buffer.writeEnum(type);
        }
        buffer.writeVarInt(pools.size());
        for (Map.Entry<AttributeType, Float> entry : pools.entrySet()) {
            buffer.writeEnum(entry.getKey());
            buffer.writeFloat(entry.getValue());
        }
    }

    public static ClientLaylineNode decode(FriendlyByteBuf buffer) {
        UUID id = buffer.readUUID();
        BlockPos position = buffer.readBlockPos();
        int genCount = buffer.readVarInt();
        Set<AttributeType> genTypes = EnumSet.noneOf(AttributeType.class);
        for (int i = 0; i < genCount; i++) {
            genTypes.add(buffer.readEnum(AttributeType.class));
        }
        int count = buffer.readVarInt();
        Map<AttributeType, Float> pools = new EnumMap<>(AttributeType.class);
        for (int i = 0; i < count; i++) {
            AttributeType type = buffer.readEnum(AttributeType.class);
            float val = buffer.readFloat();
            pools.put(type, val);
        }
        return new ClientLaylineNode(id, position, genTypes, pools);
    }
}
