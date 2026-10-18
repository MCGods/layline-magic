package com.example.laylines.clientdata;

import com.example.laylines.api.StickMode;
import javax.annotation.Nullable;

/**
 * Client-side cache for layline stick query results.
 * Stores the most recent nearest node and edge data received from the server.
 * TODO: Is this necessary? Could we just use the packet data directly in the
 * client event handler? Is it server/ client?
 */
public final class LaylineStickClientCache {

    @Nullable
    private static ClientLaylineNode closestNode;
    private static double nodeDistance = -1.0;
    @Nullable
    private static ClientLaylineEdge closestEdge;
    private static double edgeDistance = -1.0;
    private static StickMode mode = StickMode.READ_NODE; // default mode

    private LaylineStickClientCache() {
    }

    public static synchronized void update(@Nullable ClientLaylineNode node, double nodeDist,
            @Nullable ClientLaylineEdge edge, double edgeDist,
            StickMode stickMode) {
        closestNode = node;
        nodeDistance = nodeDist;
        closestEdge = edge;
        edgeDistance = edgeDist;
        mode = stickMode;
    }

    @Nullable
    public static synchronized ClientLaylineNode getClosestNode() {
        return closestNode;
    }

    public static synchronized double getNodeDistance() {
        return nodeDistance;
    }

    @Nullable
    public static synchronized ClientLaylineEdge getClosestEdge() {
        return closestEdge;
    }

    public static synchronized double getEdgeDistance() {
        return edgeDistance;
    }

    public static synchronized StickMode getMode() {
        return mode;
    }

    public static synchronized void clear() {
        closestNode = null;
        nodeDistance = -1.0;
        closestEdge = null;
        edgeDistance = -1.0;
        mode = StickMode.READ_NODE;
    }
}
