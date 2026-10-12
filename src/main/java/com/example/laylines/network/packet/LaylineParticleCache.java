package com.example.laylines.network.packet;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side cache for layline edge data used by the particle renderer.
 *
 * Stores the most recent edge data received from the server for rendering
 * firework sparkles along the layline paths.
 */
public class LaylineParticleCache {

    private static List<ClientboundLaylineSyncPacket.EdgeData> localEdges = new ArrayList<>();

    private LaylineParticleCache() {
    }

    /**
     * Sets the local edges from a server packet.
     */
    public static void setLocalEdges(List<ClientboundLaylineSyncPacket.EdgeData> edges) {
        localEdges = new ArrayList<>(edges);
    }

    /**
     * Gets the current local edges for rendering.
     */
    public static List<ClientboundLaylineSyncPacket.EdgeData> getLocalEdges() {
        return localEdges;
    }

    /**
     * Clears the local edges cache.
     */
    public static void clear() {
        localEdges.clear();
    }
}
