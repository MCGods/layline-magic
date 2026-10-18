package com.example.laylines.network.packet.laylineParticles;

import com.example.laylines.clientdata.ClientLaylineEdge;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side packet containing local layline edge data for rendering.
 * Transmits a list of ClientLaylineEdge instances with scaled energy levels
 * and no internal cell coordinates.
 */
public class ClientboundLaylineSyncPacket {

    private final List<ClientLaylineEdge> edges;

    public ClientboundLaylineSyncPacket(List<ClientLaylineEdge> edges) {
        this.edges = edges;
    }

    public ClientboundLaylineSyncPacket(FriendlyByteBuf buffer) {
        int edgeCount = buffer.readInt();
        this.edges = new ArrayList<>(edgeCount);
        for (int i = 0; i < edgeCount; i++) {
            edges.add(ClientLaylineEdge.decode(buffer));
        }
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeInt(edges.size());
        for (ClientLaylineEdge edge : edges) {
            edge.encode(buffer);
        }
    }

    public void handle(CustomPayloadEvent.Context context) {
        context.enqueueWork(() -> {
            LaylineParticleCache.setLocalEdges(edges);
        });
        context.setPacketHandled(true);
    }

    public List<ClientLaylineEdge> getEdges() {
        return edges;
    }
}
