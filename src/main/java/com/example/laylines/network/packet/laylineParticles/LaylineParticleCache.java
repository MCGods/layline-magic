package com.example.laylines.network.packet.laylineParticles;

import com.example.laylines.clientdata.ClientLaylineEdge;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side cache for layline edge data used by the particle/line renderer.
 * Stores the most recent edge DTOs received from the server.
 */
public class LaylineParticleCache {

    private static List<ClientLaylineEdge> localEdges = new ArrayList<>();

    private LaylineParticleCache() {
    }

    /**
     * Sets the local edges from a server packet.
     */
    public static void setLocalEdges(List<ClientLaylineEdge> edges) {
        localEdges = new ArrayList<>(edges);
    }

    /**
     * Gets the current local edges for rendering.
     */
    public static List<ClientLaylineEdge> getLocalEdges() {
        return localEdges;
    }

    /**
     * Clears the local edges cache.
     */
    public static void clear() {
        localEdges.clear();
    }
}
