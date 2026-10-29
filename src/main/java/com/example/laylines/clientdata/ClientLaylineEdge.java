package com.example.laylines.clientdata;

import com.example.laylines.api.LaylineNode;
import com.example.laylines.api.LaylineNode.AttributeType;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Client-side Data Transfer Object (DTO) for a layline edge.
 * Contains rendering data with full source node information and target
 * position.
 */
public record ClientLaylineEdge(
        LaylineNode nodeA,
        BlockPos nodeB) {

    /**
     * Factory to construct client edge data from server nodes.
     */
    public static ClientLaylineEdge fromServer(LaylineNode source, LaylineNode target) {
        return new ClientLaylineEdge(source, target.getPosition());
    }

    public void encode(FriendlyByteBuf buffer) {
        nodeA.encode(buffer);
        buffer.writeBlockPos(nodeB);
    }

    public static ClientLaylineEdge decode(FriendlyByteBuf buffer) {
        LaylineNode nodeA = LaylineNode.decode(buffer);
        BlockPos nodeB = buffer.readBlockPos();
        return new ClientLaylineEdge(nodeA, nodeB);
    }
}
