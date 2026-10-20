package com.example.laylines.api;

import com.example.laylines.api.LaylineNode.AttributeType;
import com.example.laylines.layout.LaylineFlowGen;
import com.example.laylines.layout.LaylineLayoutUtil;
import com.example.laylines.saveddata.LaylinesData;
import com.example.laylines.utils.LaylineGraphUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Public API for accessing the Laylines network graph.
 * Provides high-level coordinate and chunk-based queries for nodes and edges,
 * with optional elemental attribute filtering.
 * All spatial partitioning (cells) is handled internally.
 */
public final class LaylineGraphApi {

    /**
     * Default search radius in blocks (~1.5 cells) for local edge proximity
     * lookups.
     */
    public static final double DEFAULT_SEARCH_RADIUS = 384.0;

    private LaylineGraphApi() {
    }

    /**
     * Finds the closest node to the specified world coordinates, optionally
     * filtering by generating type.
     * Uses spatial hash lookups across the surrounding cells to avoid full-world
     * scans.
     *
     * @param level  The server level
     * @param pos    World coordinates to search from
     * @param filter Optional generating type to filter by (null for any node)
     * @return The closest node, or empty if none found
     */
    public static Optional<LaylineNode> getClosestNode(ServerLevel level, BlockPos pos,
            @Nullable AttributeType filter) {
        long seed = level.getSeed();
        String dimensionId = level.dimension().location().toString();

        int centerCellX = LaylineLayoutUtil.toCell(pos.getX());
        int centerCellZ = LaylineLayoutUtil.toCell(pos.getZ());

        LaylineNode closest = null;
        double closestDist = Double.MAX_VALUE;

        // Search the 3x3 cells surrounding the coordinate
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int cx = centerCellX + dx;
                int cz = centerCellZ + dz;

                LaylineNode node = LaylineGraphUtil.getDeterministicNode(seed, dimensionId, cx, cz, level);
                if (filter != null && node.getGeneratingType() != filter) {
                    continue;
                }

                double dist = LaylineLayoutUtil.horizontalDistance(pos, node.getPosition());
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = node;
                }
            }
        }

        return Optional.ofNullable(closest);
    }

    /**
     * Finds the closest node to the specified coordinates without attribute
     * filtering.
     */
    public static Optional<LaylineNode> getClosestNode(ServerLevel level, BlockPos pos) {
        return getClosestNode(level, pos, null);
    }

    /**
     * Finds the closest node to the center of a chunk, optionally filtering by
     * generating type.
     */
    public static Optional<LaylineNode> getClosestNodeToChunk(ServerLevel level, ChunkPos chunkPos,
            @Nullable AttributeType filter) {
        BlockPos centerPos = chunkPos.getMiddleBlockPosition(LaylineNode.DEFAULT_Y);
        return getClosestNode(level, centerPos, filter);
    }

    /**
     * Finds the closest edge to the specified coordinates, optionally filtering by
     * generating type.
     *
     * @param level  The server level
     * @param pos    The position to measure from
     * @param filter Optional generating attribute filter
     * @return The closest edge, or empty if none exist in the search radius
     */
    public static Optional<LaylineEdge> getClosestEdge(ServerLevel level, BlockPos pos,
            @Nullable AttributeType filter) {
        List<LaylineEdge> candidates = getEdgesNear(level, pos, DEFAULT_SEARCH_RADIUS, filter);
        return candidates.stream()
                .min(Comparator.comparingDouble(
                        edge -> LaylineLayoutUtil.pointToLineSegmentDistance(pos, edge.nodeA(), edge.nodeB())));
    }

    /**
     * Finds the closest edge to the specified coordinates without attribute
     * filtering.
     */
    public static Optional<LaylineEdge> getClosestEdge(ServerLevel level, BlockPos pos) {
        return getClosestEdge(level, pos, null);
    }

    /**
     * Finds the closest edge to the center of a chunk, optionally filtering by
     * generating type.
     */
    public static Optional<LaylineEdge> getClosestEdgeToChunk(ServerLevel level, ChunkPos chunkPos,
            @Nullable AttributeType filter) {
        BlockPos centerPos = chunkPos.getMiddleBlockPosition(LaylineNode.DEFAULT_Y);
        return getClosestEdge(level, centerPos, filter);
    }

    /**
     * Retrieves all edges within a specified horizontal radius of a world position.
     *
     * @param level  The server level
     * @param pos    Center position
     * @param radius Maximum horizontal block distance
     * @param filter Optional attribute filter
     * @return List of matching edges
     */
    public static List<LaylineEdge> getEdgesNear(ServerLevel level, BlockPos pos, double radius,
            @Nullable AttributeType filter) {
        long seed = level.getSeed();
        String dimensionId = level.dimension().location().toString();

        int minCellX = LaylineLayoutUtil.toCell((int) (pos.getX() - radius));
        int maxCellX = LaylineLayoutUtil.toCell((int) (pos.getX() + radius));
        int minCellZ = LaylineLayoutUtil.toCell((int) (pos.getZ() - radius));
        int maxCellZ = LaylineLayoutUtil.toCell((int) (pos.getZ() + radius));

        Set<LaylineEdge> result = new HashSet<>();
        for (int cx = minCellX; cx <= maxCellX; cx++) {
            for (int cz = minCellZ; cz <= maxCellZ; cz++) {
                List<LaylineEdge> cellEdges = LaylineGraphUtil.getEdgesForCell(seed, dimensionId, cx, cz, level);
                for (LaylineEdge edge : cellEdges) {
                    if (filter != null && edge.getGeneratingType() != filter) {
                        continue;
                    }
                    double dist = LaylineLayoutUtil.pointToLineSegmentDistance(pos, edge.nodeA(), edge.nodeB());
                    if (dist <= radius) {
                        result.add(edge);
                    }
                }
            }
        }

        return new ArrayList<>(result);
    }

    /**
     * Retrieves all edges associated with the cell containing a given chunk.
     */
    public static List<LaylineEdge> getEdgesForChunk(ServerLevel level, ChunkPos chunkPos) {
        long seed = level.getSeed();
        String dimensionId = level.dimension().location().toString();
        int cellX = LaylineLayoutUtil.chunkToCell(chunkPos.x);
        int cellZ = LaylineLayoutUtil.chunkToCell(chunkPos.z);
        return LaylineGraphUtil.getEdgesForCell(seed, dimensionId, cellX, cellZ, level);
    }

    /**
     * Retrieves all edges in the world associated with nodes that generate the
     * given attribute.
     */
    public static Set<LaylineEdge> getEdgesForAttribute(ServerLevel level, AttributeType attribute) {
        LaylinesData data = LaylinesData.get(level.getServer());
        return LaylineGraphUtil.getEdgesForAttribute(
                level.getSeed(),
                level.dimension().location().toString(),
                attribute,
                data);
    }

    /**
     * Looks up an existing saved node by its UUID.
     */
    public static Optional<LaylineNode> getNode(ServerLevel level, UUID id) {
        LaylinesData data = LaylinesData.get(level.getServer());
        return Optional.ofNullable(data.getNode(id));
    }

    /**
     * Returns the live set of nodes currently residing in loaded chunks.
     */
    public static Set<LaylineNode> getLoadedNodes(ServerLevel level) {
        LaylinesData data = LaylinesData.get(level.getServer());

        return data.getLoadedNodes();
    }

    /**
     * Registers a new or modified node into the world's saved data.
     */
    public static void putNode(ServerLevel level, LaylineNode node) {
        LaylinesData.get(level.getServer()).putNode(node);
    }

    /**
     * Retrieves all layline edges where at least one endpoint node resides within a
     * loaded chunk.
     */
    public static List<LaylineEdge> getLoadedEdges(ServerLevel level) {
        LaylinesData data = LaylinesData.get(level.getServer());
        Set<LaylineNode> loadedNodes = data.getLoadedNodes();
        if (loadedNodes.isEmpty()) {
            data.syncLoadedNodes(level);
            loadedNodes = data.getLoadedNodes();
        }

        if (loadedNodes.isEmpty()) {
            return List.of();
        }

        long seed = level.getSeed();
        String dimensionId = level.dimension().location().toString();
        Set<LaylineEdge> result = new HashSet<>();

        for (LaylineNode node : loadedNodes) {
            int cx = LaylineLayoutUtil.toCell(node.getPosition().getX());
            int cz = LaylineLayoutUtil.toCell(node.getPosition().getZ());

            // East & South incident edges
            result.addAll(LaylineGraphUtil.getEdgesForCell(seed, dimensionId, cx, cz, level));

            // West neighbor incident edge
            if (LaylineFlowGen.isEdgeConnected(seed, dimensionId, cx - 1, cz, cx, cz)) {
                LaylineNode west = LaylineGraphUtil.getDeterministicNode(seed, dimensionId, cx - 1, cz, level);
                result.add(LaylineFlowGen.createDirectedEdge(west, node, seed, dimensionId));
            }

            // North neighbor incident edge
            if (LaylineFlowGen.isEdgeConnected(seed, dimensionId, cx, cz - 1, cx, cz)) {
                LaylineNode north = LaylineGraphUtil.getDeterministicNode(seed, dimensionId, cx, cz - 1, level);
                result.add(LaylineFlowGen.createDirectedEdge(north, node, seed, dimensionId));
            }
        }

        return new ArrayList<>(result);
    }
}
