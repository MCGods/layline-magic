package com.example.laylines.clientdata;

import com.example.laylines.network.LaylineNode;
import com.example.laylines.network.LaylineNode.AttributeType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Client-side Data Transfer Object (DTO) for a layline edge.
 * Contains only rendering and interaction data; never exposes internal cell coordinates
 * or raw server capacities.
 */
public record ClientLaylineEdge(
        BlockPos nodeA,
        BlockPos nodeB,
        AttributeType dominantType,
        int scaledEnergy
) {

    /**
     * Scales a server energy pool value to the client range [0, 10] using integer division by 10.
     */
    public static int scaleEnergy(float energy) {
        if (energy <= 0.0f) {
            return 0;
        }
        return Math.min(10, (int) (energy / 10.0f));
    }

    /**
     * Factory to construct client edge data from server nodes.
     */
    public static ClientLaylineEdge fromServer(LaylineNode source, LaylineNode target) {
        AttributeType type = source.getGeneratingType() != null ? source.getGeneratingType() : AttributeType.EARTH;
        int scaled = scaleEnergy(source.getCurrentValue(type));
        return new ClientLaylineEdge(source.getPosition(), target.getPosition(), type, scaled);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(nodeA);
        buffer.writeBlockPos(nodeB);
        buffer.writeEnum(dominantType);
        buffer.writeVarInt(scaledEnergy);
    }

    public static ClientLaylineEdge decode(FriendlyByteBuf buffer) {
        BlockPos nodeA = buffer.readBlockPos();
        BlockPos nodeB = buffer.readBlockPos();
        AttributeType dominantType = buffer.readEnum(AttributeType.class);
        int scaledEnergy = buffer.readVarInt();
        return new ClientLaylineEdge(nodeA, nodeB, dominantType, scaledEnergy);
    }
}
