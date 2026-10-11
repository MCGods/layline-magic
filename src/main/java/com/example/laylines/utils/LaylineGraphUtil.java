package com.example.laylines.utils;

import com.example.laylines.network.LaylineNode;
import com.example.laylines.network.LaylineNode.AttributeType;
import com.example.laylines.saveddata.LaylinesData;

import net.minecraft.core.BlockPos;

import java.util.Random;
import java.util.UUID;

/**
 * Seed-deterministic 2D layout for the layline web (README Section 1).
 *
 * The world is partitioned into {@link #CELL_SIZE}×{@link #CELL_SIZE} squares.
 * Each cell owns exactly one node whose X/Z offset is mixed from the world
 * seed, cell index, and dimension id — independent of chunk load order.
 *
 * Delaunay edges are not computed here yet; this class only places vertices.
 */
public final class LaylineGraphUtil {

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
     * Returns the saved node for this cell, or creates and persists one.
     * Identity is the deterministic cell BlockPos (not UUID), so the first
     * visit to a cell wins and later visits reuse it.
     */
    public static LaylineNode getOrCreateNodeForCell(long seed, String dimensionId, int cellX, int cellZ,
            LaylinesData data) {
        CellRoll roll = rollCell(seed, dimensionId, cellX, cellZ);

        for (LaylineNode existing : data.getNodes().values()) {
            if (existing.getPosition().equals(roll.position)) {
                return existing;
            }
        }

        LaylineNode created = new LaylineNode(roll.id, roll.position, roll.generatingType);
        data.putNode(created);
        return created;
    }

    /**
     * Closest node to {@code playerPos} among the player's cell and its 8
     * neighbors (3×3). Missing cells are created so teleport/chunk order
     * cannot change the local graph.
     */
    public static LaylineNode findClosestNode(long seed, String dimensionId, BlockPos playerPos, LaylinesData data) {
        int cellX = toCell(playerPos.getX());
        int cellZ = toCell(playerPos.getZ());

        LaylineNode closest = null;
        double closestDist = Double.MAX_VALUE;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                LaylineNode node = getOrCreateNodeForCell(seed, dimensionId, cellX + dx, cellZ + dz, data);
                double dist = horizontalDistance(playerPos, node.getPosition());
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = node;
                }
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
