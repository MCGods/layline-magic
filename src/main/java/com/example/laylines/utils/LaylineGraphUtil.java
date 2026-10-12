package com.example.laylines.utils;

import com.example.laylines.network.LaylineNode;
import com.example.laylines.network.LaylineNode.AttributeType;
import com.example.laylines.saveddata.LaylinesData;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
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
     * Represents a connection between two layline nodes.
     * Edges form the infinite web that carries energy across the world.
     */
    public static record LaylineEdge(BlockPos nodeA, BlockPos nodeB) {
    }

    /**
     * Block width/depth of one layout cell. A random 0–255 offset inside the
     * cell yields typical neighbor spacing around ~200 blocks radially.
     */
    public static final int CELL_SIZE = 256;

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
        // Check LaylinesData for existing nodes in this cell
        LaylinesData data = LaylinesData.get(level.getServer());
        BlockPos cellPos = positionForCell(seed, dimensionId, cellX, cellZ);

        // Look for a node that falls within this cell's bounds
        int cellMinX = cellX * CELL_SIZE;
        int cellMinZ = cellZ * CELL_SIZE;
        int cellMaxX = cellMinX + CELL_SIZE - 1;
        int cellMaxZ = cellMinZ + CELL_SIZE - 1;

        for (LaylineNode node : data.getNodes().values()) {
            BlockPos nodePos = node.getPosition();
            if (nodePos.getX() >= cellMinX && nodePos.getX() <= cellMaxX &&
                    nodePos.getZ() >= cellMinZ && nodePos.getZ() <= cellMaxZ) {
                // Found a node in this cell (could be structure-anchored)
                return nodePos;
            }
        }

        // Fallback to standard seed-offset calculation for ungenerated chunks
        return cellPos;
    }

    /**
     * Generates runtime edges for a cell and its East/South neighbors.
     *
     * Calculates the true deterministic positions of the current cell, its East
     * neighbor
     * (+1 cellX), and its South neighbor (+1 cellZ) by pulling coordinates from
     * LaylinesData. If a node hasn't been generated yet (ungenerated chunks), it
     * temporarily falls back to seed-math prediction for smooth stick reading.
     * Returns a list of LaylineEdge connections between them, creating a continuous
     * grid web that bends toward structure-anchored nodes.
     *
     * @param seed        World seed
     * @param dimensionId Dimension identifier
     * @param cellX       Cell X coordinate
     * @param cellZ       Cell Z coordinate
     * @param level       Server level for accessing LaylinesData
     * @return List of edges originating from this cell
     */
    public static List<LaylineEdge> getEdgesForCell(long seed, String dimensionId, int cellX, int cellZ,
            ServerLevel level) {
        List<LaylineEdge> edges = new ArrayList<>();

        BlockPos currentPos = getDeterministicNodePos(seed, dimensionId, cellX, cellZ, level);
        BlockPos eastPos = getDeterministicNodePos(seed, dimensionId, cellX + 1, cellZ, level);
        BlockPos southPos = getDeterministicNodePos(seed, dimensionId, cellX, cellZ + 1, level);

        // Create edge to East neighbor
        edges.add(new LaylineEdge(currentPos, eastPos));

        // Create edge to South neighbor
        edges.add(new LaylineEdge(currentPos, southPos));

        return edges;
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
        int cellX = toCell(playerPos.getX());
        int cellZ = toCell(playerPos.getZ());

        LaylineEdge closestEdge = null;
        double closestDist = Double.MAX_VALUE;

        // Generate edges for the 3x3 cell grid surrounding the player
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                List<LaylineEdge> cellEdges = getEdgesForCell(seed, dimensionId, cellX + dx, cellZ + dz, level);

                for (LaylineEdge edge : cellEdges) {
                    double dist = pointToLineSegmentDistance(playerPos, edge.nodeA(), edge.nodeB());
                    if (dist < closestDist) {
                        closestDist = dist;
                        closestEdge = edge;
                    }
                }
            }
        }

        return closestEdge;
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
}
