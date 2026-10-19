package com.example.laylines.layout;

import net.minecraft.core.BlockPos;

/**
 * Internal layout, grid conversion, and geometric math utilities.
 * Encapsulates all cell-level calculations so gameplay and API code
 * operate only with world coordinates (BlockPos / ChunkPos).
 */
public final class LaylineLayoutUtil {

    /**
     * Block width/depth of one layout cell.
     */
    public static final int CELL_SIZE = 256;

    private LaylineLayoutUtil() {
    }

    /**
     * Converts a block coordinate into a cell index using floor division.
     */
    public static int toCell(int blockCoord) {
        return Math.floorDiv(blockCoord, CELL_SIZE);
    }

    /**
     * Converts a chunk coordinate into a cell index.
     */
    public static int chunkToCell(int chunkCoord) {
        return Math.floorDiv(chunkCoord * 16, CELL_SIZE);
    }

    /**
     * Minimum block coordinate for a given cell index.
     */
    public static int cellMinBlock(int cell) {
        return cell * CELL_SIZE;
    }

    /**
     * Maximum block coordinate for a given cell index (inclusive).
     */
    public static int cellMaxBlock(int cell) {
        return cell * CELL_SIZE + CELL_SIZE - 1;
    }

    /**
     * Minimum chunk coordinate for a given cell index.
     */
    public static int cellMinChunk(int cell) {
        return cell * (CELL_SIZE / 16);
    }

    /**
     * Maximum chunk coordinate for a given cell index (inclusive).
     */
    public static int cellMaxChunk(int cell) {
        return cell * (CELL_SIZE / 16) + (CELL_SIZE / 16) - 1;
    }

    /**
     * Packs 2D cell coordinates into a 64-bit primitive key for fast hash mapping.
     */
    public static long packCell(int cellX, int cellZ) {
        return (((long) cellX) << 32) | (((long) cellZ) & 0xFFFFFFFFL);
    }

    public static int unpackCellX(long packed) {
        return (int) (packed >> 32);
    }

    public static int unpackCellZ(long packed) {
        return (int) packed;
    }

    /**
     * Mixes world seed, dimension ID, and cell coordinates into a 64-bit RNG seed.
     */
    public static long compoundSeed(long worldSeed, String dimensionId, int cellX, int cellZ) {
        long mixed = worldSeed;
        mixed ^= (long) dimensionId.hashCode() * 0xFF51AFD7ED558CCDL;
        mixed = Long.rotateLeft(mixed, 31);
        mixed ^= (long) cellX * 341873128712L;
        mixed = Long.rotateLeft(mixed, 17);
        mixed ^= (long) cellZ * 132897987541L;
        // MurmurHash3-style finalizer
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= mixed >>> 33;
        mixed *= 0xc4ceb9fe1a85ec53L;
        mixed ^= mixed >>> 33;
        return mixed;
    }

    /**
     * Calculates 2D horizontal distance (X/Z only, ignoring Y).
     */
    public static double horizontalDistance(BlockPos a, BlockPos b) {
        double dx = (double) a.getX() - b.getX();
        double dz = (double) a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Calculates the shortest 2D distance from a point to a line segment (X/Z only).
     */
    public static double pointToLineSegmentDistance(BlockPos point, BlockPos lineStart, BlockPos lineEnd) {
        double px = point.getX();
        double pz = point.getZ();
        double x1 = lineStart.getX();
        double z1 = lineStart.getZ();
        double x2 = lineEnd.getX();
        double z2 = lineEnd.getZ();

        double dx = x2 - x1;
        double dz = z2 - z1;

        if (dx == 0 && dz == 0) {
            return Math.sqrt((px - x1) * (px - x1) + (pz - z1) * (pz - z1));
        }

        double t = ((px - x1) * dx + (pz - z1) * dz) / (dx * dx + dz * dz);
        t = Math.max(0, Math.min(1, t));

        double closestX = x1 + t * dx;
        double closestZ = z1 + t * dz;

        return Math.sqrt((px - closestX) * (px - closestX) + (pz - closestZ) * (pz - closestZ));
    }
}

