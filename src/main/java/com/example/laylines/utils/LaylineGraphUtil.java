package com.example.laylines.utils;

import com.example.laylines.layout.LaylineFlowGen;
import com.example.laylines.layout.LaylineLayoutUtil;
import com.example.laylines.network.LaylineEdge;
import com.example.laylines.network.LaylineNode;
import com.example.laylines.network.LaylineNode.AttributeType;
import com.example.laylines.saveddata.LaylinesData;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Seed-deterministic 2D layout for the layline web (README Section 1).
 *
 * The world is partitioned into {@link #CELL_SIZE}×{@link #CELL_SIZE} squares.
 * Each cell owns exactly one node whose X/Z offset is mixed from the world
 * seed, cell index, and dimension id — independent of chunk load order.
 *
 * This utility uses a unified database model where LaylinesData is the source
 * of truth. Nodes are physically instantiated during chunk generation by
 * LaylineWorldGenListener, and edges are calculated by pulling coordinates
 * from the database with seed-math fallback for ungenerated chunks.
 */
public final class LaylineGraphUtil {

    /**
     * Block width/depth of one layout cell. A random 0–255 offset inside the
     * cell yields typical neighbor spacing around ~200 blocks radially.
     */
    public static final int CELL_SIZE = LaylineLayoutUtil.CELL_SIZE;

    private LaylineGraphUtil() {
    }

    /**
     * Mixes world seed, cell index, and dimension into a 64-bit RNG seed.
     * Same inputs always produce the same {@link Random} stream.
     */
    public static long compoundSeed(long worldSeed, String dimensionId, int cellX, int cellZ) {
        long mixed = worldSeed;
        mixed ^= (long) dimensionId.hashCode() * 0xFF51AFD7ED558CCDL;
        mixed = Long.rotateLeft(mixed, 31);
        mixed ^= (long) cellX * 341873128712L;
        mixed = Long.rotateLeft(mixed, 17);
        mixed ^= (long) cellZ * 132897987541L;
        // MurmurHash3-style finalizer so nearby cells do not correlate.
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= mixed >>> 33;
        mixed *= 0xc4ceb9fe1a85ec53L;
        mixed ^= mixed >>> 33;
        return mixed;
    }

    /**
     * Block-space cell index. Uses floor division so negative coordinates
     * (west/north of origin) still map into stable grid slots.
     */
    public static int toCell(int blockCoord) {
        return Math.floorDiv(blockCoord, CELL_SIZE);
    }

    /**
     * Deterministic node BlockPos for one cell: random X/Z offset in
     * {@code [0, CELL_SIZE)}, Y fixed at {@link LaylineNode#DEFAULT_Y}.
     */
    public static BlockPos positionForCell(long worldSeed, String dimensionId, int cellX, int cellZ) {
        return rollCell(worldSeed, dimensionId, cellX, cellZ).position;
    }

    /**
     * Closest node to {@code playerPos} using a pure data-driven approach.
     *
     * Iterates over all nodes currently registered in LaylinesData and performs
     * a 2D distance calculation (X/Z only, ignoring Y) to find the true closest
     * node.
     * This approach works correctly with our unified database model where all
     * nodes are instantiated during chunk generation.
     *
     * @param seed        World seed (unused in data-driven mode but kept for API
     *                    compatibility)
     * @param dimensionId Dimension ID (unused in data-driven mode but kept for API
     *                    compatibility)
     * @param playerPos   The player's position to measure distance from
     * @param data        The LaylinesData containing all registered nodes
     * @return The closest node, or null if no nodes exist
     */
    public static LaylineNode findClosestNode(long seed, String dimensionId, BlockPos playerPos, LaylinesData data) {
        LaylineNode closest = null;
        double closestDist = Double.MAX_VALUE;

        // Iterate over all actual saved nodes from LaylinesData
        for (LaylineNode node : data.getNodes().values()) {
            double dist = horizontalDistance(playerPos, node.getPosition());
            if (dist < closestDist) {
                closestDist = dist;
                closest = node;
            }
        }

        return closest;
    }

    /** 2D distance on the layout plane (X/Z only). */
    public static double horizontalDistance(BlockPos a, BlockPos b) {
        double dx = (double) a.getX() - b.getX();
        double dz = (double) a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Gets the deterministic node position for a cell using the database as source
     * of truth.
     *
     * First checks LaylinesData for existing nodes in this cell (which includes
     * structure-anchored nodes from world gen). If found, returns that position.
     * Otherwise, falls back to the standard cell seed-offset mathematical
     * calculation
     * for smooth edge prediction in ungenerated chunks.
     *
     * This approach is data-driven and consistent with our unified database model -
     * nodes are instantiated during chunk load (LaylineWorldGenListener), so we
     * query
     * the saved data first. The seed-math fallback ensures the stick reads smoothly
     * even when looking at ungenerated chunks.
     *
     * @param seed        World seed
     * @param dimensionId Dimension identifier
     * @param cellX       Cell X coordinate
     * @param cellZ       Cell Z coordinate
     * @param level       Server level for accessing LaylinesData
     * @return The deterministic node position for this cell
     */
    public static BlockPos getDeterministicNodePos(long seed, String dimensionId, int cellX, int cellZ,
            ServerLevel level) {
        return getDeterministicNode(seed, dimensionId, cellX, cellZ, level).getPosition();
    }

    /**
     * Resolves the complete LaylineNode for a cell. Queries LaylinesData first;
     * if not yet generated, falls back to a deterministic virtual LaylineNode.
     */
    public static LaylineNode getDeterministicNode(long seed, String dimensionId, int cellX, int cellZ,
            ServerLevel level) {
        LaylinesData data = LaylinesData.get(level.getServer());

        LaylineNode existing = data.getNodeByCell(cellX, cellZ);
        if (existing != null) {
            return existing;
        }

        return createDeterministicNode(seed, dimensionId, cellX, cellZ);
    }

    /**
     * Creates a deterministic LaylineNode based on seed math for ungenerated
     * chunks.
     */
    public static LaylineNode createDeterministicNode(long worldSeed, String dimensionId, int cellX, int cellZ) {
        CellRoll roll = rollCell(worldSeed, dimensionId, cellX, cellZ);
        return new LaylineNode(roll.id(), roll.position(), roll.generatingType());
    }

    /**
     * Generates runtime edges for a cell and its East/South neighbors.
     * Each edge stores complete LaylineNode endpoints with the first node as
     * source.
     */
    public static List<LaylineEdge> getEdgesForCell(long seed, String dimensionId, int cellX, int cellZ,
            ServerLevel level) {
        List<LaylineEdge> edges = new ArrayList<>();

        LaylineNode currentNode = getDeterministicNode(seed, dimensionId, cellX, cellZ, level);

        // Check East neighbor (cellX + 1, cellZ)
        if (LaylineFlowGen.isEdgeConnected(seed, dimensionId, cellX, cellZ, cellX + 1, cellZ)) {
            LaylineNode eastNode = getDeterministicNode(seed, dimensionId, cellX + 1, cellZ, level);
            edges.add(LaylineFlowGen.createDirectedEdge(currentNode, eastNode, seed, dimensionId));
        }

        // Check South neighbor (cellX, cellZ + 1)
        if (LaylineFlowGen.isEdgeConnected(seed, dimensionId, cellX, cellZ, cellX, cellZ + 1)) {
            LaylineNode southNode = getDeterministicNode(seed, dimensionId, cellX, cellZ + 1, level);
            edges.add(LaylineFlowGen.createDirectedEdge(currentNode, southNode, seed, dimensionId));
        }

        return edges;
    }

    /**
     * Returns the grid edges connected to nodes generating {@code attribute}.
     * Each matching node contributes its connected adjacent edges.
     */
    public static Set<LaylineEdge> getEdgesForAttribute(long seed, String dimensionId,
            AttributeType attribute, LaylinesData data) {
        Map<CellKey, LaylineNode> nodesByCell = new HashMap<>();
        for (LaylineNode node : data.getNodes().values()) {
            BlockPos position = node.getPosition();
            nodesByCell.put(new CellKey(toCell(position.getX()), toCell(position.getZ())), node);
        }

        Set<LaylineEdge> edges = new HashSet<>();
        for (LaylineNode node : data.getNodesGenerating(attribute)) {
            BlockPos position = node.getPosition();
            CellKey cell = new CellKey(toCell(position.getX()), toCell(position.getZ()));
            LaylineNode current = nodeForCell(seed, dimensionId, cell, nodesByCell);

            // East
            if (LaylineFlowGen.isEdgeConnected(seed, dimensionId, cell.x(), cell.z(), cell.x() + 1, cell.z())) {
                LaylineNode east = nodeForCell(seed, dimensionId, new CellKey(cell.x() + 1, cell.z()), nodesByCell);
                edges.add(LaylineFlowGen.createDirectedEdge(current, east, seed, dimensionId));
            }
            // South
            if (LaylineFlowGen.isEdgeConnected(seed, dimensionId, cell.x(), cell.z(), cell.x(), cell.z() + 1)) {
                LaylineNode south = nodeForCell(seed, dimensionId, new CellKey(cell.x(), cell.z() + 1), nodesByCell);
                edges.add(LaylineFlowGen.createDirectedEdge(current, south, seed, dimensionId));
            }
            // West
            if (LaylineFlowGen.isEdgeConnected(seed, dimensionId, cell.x(), cell.z(), cell.x() - 1, cell.z())) {
                LaylineNode west = nodeForCell(seed, dimensionId, new CellKey(cell.x() - 1, cell.z()), nodesByCell);
                edges.add(LaylineFlowGen.createDirectedEdge(current, west, seed, dimensionId));
            }
            // North
            if (LaylineFlowGen.isEdgeConnected(seed, dimensionId, cell.x(), cell.z(), cell.x(), cell.z() - 1)) {
                LaylineNode north = nodeForCell(seed, dimensionId, new CellKey(cell.x(), cell.z() - 1), nodesByCell);
                edges.add(LaylineFlowGen.createDirectedEdge(current, north, seed, dimensionId));
            }
        }
        return edges;
    }

    private static LaylineNode nodeForCell(long seed, String dimensionId, CellKey cell,
            Map<CellKey, LaylineNode> nodesByCell) {
        LaylineNode node = nodesByCell.get(cell);
        return node == null
                ? createDeterministicNode(seed, dimensionId, cell.x, cell.z)
                : node;
    }

    /**
     * Finds the closest edge to the player's position using a localized 3x3 cell
     * lookup.
     *
     * Restricts calculation strictly to a 3x3 grid of cells surrounding the player
     * to
     * prevent server ticking lag. Generates runtime edges for those 9 cells,
     * performs
     * a 2D point-to-line-segment distance calculation (ignoring Y), and returns the
     * absolute closest edge.
     *
     * @param seed        World seed
     * @param dimensionId Dimension identifier
     * @param playerPos   The player's position
     * @param level       Server level for structure placement lookup
     * @return The closest edge, or null if no edges exist
     */
    public static LaylineEdge findClosestEdge(long seed, String dimensionId, BlockPos playerPos, ServerLevel level) {
        return com.example.laylines.api.LaylineGraphApi.getClosestEdge(level, playerPos).orElse(null);
    }

    /**
     * Calculates a random position near an edge, within a specified radius.
     * 
     * @param edge   The edge to sample near
     * @param radius The maximum distance from the edge to sample
     * @param level  The server level
     * @return A random position near the edge, within the specified radius
     */
    public static BlockPos randomPositionNearEdge(LaylineEdge edge,
            double radius, ServerLevel level) {
        double deltaX = edge.nodeB().getX() - edge.nodeA().getX();
        double deltaZ = edge.nodeB().getZ() - edge.nodeA().getZ();
        double length = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        if (length == 0.0) {
            return edge.nodeA();
        }

        double along = level.random.nextDouble();
        double offset = (level.random.nextDouble() * 2.0 - 1.0) * radius;
        double x = edge.nodeA().getX() + deltaX * along - deltaZ / length * offset;
        double z = edge.nodeA().getZ() + deltaZ * along + deltaX / length * offset;
        return new BlockPos((int) Math.round(x), 0, (int) Math.round(z));
    }

    /**
     * Calculates the shortest distance from a point to a line segment in 2D (X/Z
     * only).
     *
     * @param point     The point to measure from
     * @param lineStart The start of the line segment
     * @param lineEnd   The end of the line segment
     * @return The shortest distance from the point to the line segment
     */
    public static double pointToLineSegmentDistance(BlockPos point, BlockPos lineStart, BlockPos lineEnd) {
        double px = point.getX();
        double pz = point.getZ();
        double x1 = lineStart.getX();
        double z1 = lineStart.getZ();
        double x2 = lineEnd.getX();
        double z2 = lineEnd.getZ();

        // Vector from lineStart to lineEnd
        double dx = x2 - x1;
        double dz = z2 - z1;

        // If the line segment is a point, return distance to that point
        if (dx == 0 && dz == 0) {
            return Math.sqrt((px - x1) * (px - x1) + (pz - z1) * (pz - z1));
        }

        // Project point onto the line, clamped to the segment
        double t = ((px - x1) * dx + (pz - z1) * dz) / (dx * dx + dz * dz);
        t = Math.max(0, Math.min(1, t));

        // Closest point on the segment
        double closestX = x1 + t * dx;
        double closestZ = z1 + t * dz;

        // Distance from point to closest point on segment
        return Math.sqrt((px - closestX) * (px - closestX) + (pz - closestZ) * (pz - closestZ));
    }

    /**
     * One RNG stream per cell: offset first (layout), then identity/type so
     * moving a node does not require a different mixing formula.
     */
    private static CellRoll rollCell(long worldSeed, String dimensionId, int cellX, int cellZ) {
        Random random = new Random(compoundSeed(worldSeed, dimensionId, cellX, cellZ));
        int offsetX = random.nextInt(CELL_SIZE);
        int offsetZ = random.nextInt(CELL_SIZE);
        BlockPos position = new BlockPos(
                cellX * CELL_SIZE + offsetX,
                LaylineNode.DEFAULT_Y,
                cellZ * CELL_SIZE + offsetZ);
        UUID id = new UUID(random.nextLong(), random.nextLong());
        AttributeType generatingType = rollGeneratingType(random);
        return new CellRoll(position, id, generatingType);
    }

    /** Same 50% generation rule as {@link LaylineNode}, driven by the cell RNG. */
    private static AttributeType rollGeneratingType(Random random) {
        if (random.nextBoolean()) {
            return null;
        }
        AttributeType[] types = AttributeType.values();
        return types[random.nextInt(types.length)];
    }

    private record CellRoll(BlockPos position, UUID id, AttributeType generatingType) {
    }

    private record CellKey(int x, int z) {
    }
}
