package com.example.laylines.network.packet;

import com.example.laylines.network.LaylineNode.AttributeType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side packet containing local layline edge data for particle rendering.
 *
 * Transmits a list of LaylineEdge positions along with their associated
 * AttributeType for color rendering. The client stores this in a cache
 * for the particle renderer to use.
 */
public class ClientboundLaylineSyncPacket {

    private final List<EdgeData> edges;

    public ClientboundLaylineSyncPacket(List<EdgeData> edges) {
        this.edges = edges;
    }

    public ClientboundLaylineSyncPacket(FriendlyByteBuf buffer) {
        int edgeCount = buffer.readInt();
        this.edges = new ArrayList<>(edgeCount);
        for (int i = 0; i < edgeCount; i++) {
            BlockPos nodeA = buffer.readBlockPos();
            BlockPos nodeB = buffer.readBlockPos();
            AttributeType attributeType = buffer.readEnum(AttributeType.class);
            edges.add(new EdgeData(nodeA, nodeB, attributeType));
        }
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeInt(edges.size());
        for (EdgeData edge : edges) {
            buffer.writeBlockPos(edge.nodeA());
            buffer.writeBlockPos(edge.nodeB());
            buffer.writeEnum(edge.attributeType());
        }
    }

    public void handle(CustomPayloadEvent.Context context) {
        context.enqueueWork(() -> {
            // Store edges in client-side cache for particle renderer
            LaylineParticleCache.setLocalEdges(edges);
        });
        context.setPacketHandled(true);
    }

    public List<EdgeData> getEdges() {
        return edges;
    }

    /**
     * Represents a single edge with its positions and attribute type for coloring.
     */
    public record EdgeData(BlockPos nodeA, BlockPos nodeB, AttributeType attributeType) {
    }
}
