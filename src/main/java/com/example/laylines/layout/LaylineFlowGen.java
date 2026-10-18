package com.example.laylines.layout;

import com.example.laylines.network.LaylineEdge;
import com.example.laylines.network.LaylineNode;

/**
 * Seed-deterministic layline network connectivity and flow generation
 * inspired by 2D Perlin/Simplex gradient or Murmur-based noise.
 *
 * Implements:
 * 1. Degree regulation: Nodes average ~3 connections (between 1 and 4).
 * 2. Minimum connectivity: No isolated nodes (dead ends / tributaries at
 * ~4.7%).
 * 3. Directed flow: Uses a continuous seed-based scalar potential field (DAG)
 * so energy
 * flows smoothly downstream in a consistent direction without cycles.
 * 4. Anti-sink/source: Prevents 4 incoming or 4 outgoing edges at a single
 * node.
 */
public final class LaylineFlowGen {

    private LaylineFlowGen() {
    }

    /**
     * Determines whether an undirected edge exists between two adjacent cells.
     * Symmetric: returns the exact same value regardless of argument order.
     */
    public static boolean isEdgeConnected(long worldSeed, String dimensionId, int x1, int z1, int x2, int z2) {
        // Canonical ordering of endpoints for symmetry
        int minX, minZ, maxX, maxZ;
        if (x1 < x2 || (x1 == x2 && z1 <= z2)) {
            minX = x1;
            minZ = z1;
            maxX = x2;
            maxZ = z2;
        } else {
            minX = x2;
            minZ = z2;
            maxX = x1;
            maxZ = z1;
        }
        // MurmurHash3 PRNG
        long edgeHash = LaylineLayoutUtil.compoundSeed(worldSeed, dimensionId, minX, minZ);
        edgeHash ^= (long) maxX * 83492791L;
        edgeHash = Long.rotateLeft(edgeHash, 17);
        edgeHash ^= (long) maxZ * 53495867L;
        // Murmur3 finalizer
        edgeHash ^= edgeHash >>> 33;
        edgeHash *= 0xff51afd7ed558ccdL;
        edgeHash ^= edgeHash >>> 33;

        double roll = ((edgeHash >>> 16) & 0xFFFF) / 65536.0;

        // ~75% chance of connection yields an average node degree of ~3.0
        if (roll < 0.75) {
            return true;
        }

        // Guarantee no node is isolated: if all 4 neighbor edges roll false, keep the
        // steepest edge
        return isGuaranteedEdgeForNode(worldSeed, dimensionId, minX, minZ, maxX, maxZ);
    }

    /**
     * Creates a directed LaylineEdge between two adjacent nodes.
     * The higher-potential node is the source; the lower-potential node is the
     * target.
     * Also applies anti-sink/source rules so no node has 4 in or 4 out.
     */
    public static LaylineEdge createDirectedEdge(LaylineNode nodeA, LaylineNode nodeB, long worldSeed,
            String dimensionId) {
        int ax = LaylineLayoutUtil.toCell(nodeA.getPosition().getX());
        int az = LaylineLayoutUtil.toCell(nodeA.getPosition().getZ());
        int bx = LaylineLayoutUtil.toCell(nodeB.getPosition().getX());
        int bz = LaylineLayoutUtil.toCell(nodeB.getPosition().getZ());

        double potA = computePotential(worldSeed, dimensionId, ax, az);
        double potB = computePotential(worldSeed, dimensionId, bx, bz);

        // Check if nodeA or nodeB is a local extremum
        boolean aIsPeak = isLocalPeak(worldSeed, dimensionId, ax, az, potA);
        boolean aIsPit = isLocalPit(worldSeed, dimensionId, ax, az, potA);
        boolean bIsPeak = isLocalPeak(worldSeed, dimensionId, bx, bz, potB);
        boolean bIsPit = isLocalPit(worldSeed, dimensionId, bx, bz, potB);

        // Anti-source rule for local peaks: highest neighbor flows into the peak
        if (aIsPeak && isHighestNeighbor(worldSeed, dimensionId, ax, az, bx, bz)) {
            return new LaylineEdge(nodeB, nodeA); // B flows into A
        }
        if (bIsPeak && isHighestNeighbor(worldSeed, dimensionId, bx, bz, ax, az)) {
            return new LaylineEdge(nodeA, nodeB); // A flows into B
        }

        // Anti-sink rule for local pits: pit flows into its lowest neighbor
        if (aIsPit && isLowestNeighbor(worldSeed, dimensionId, ax, az, bx, bz)) {
            return new LaylineEdge(nodeA, nodeB); // A flows out to B
        }
        if (bIsPit && isLowestNeighbor(worldSeed, dimensionId, bx, bz, ax, az)) {
            return new LaylineEdge(nodeB, nodeA); // B flows out to A
        }

        // Standard gradient flow: higher potential -> lower potential
        if (potA >= potB) {
            return new LaylineEdge(nodeA, nodeB);
        } else {
            return new LaylineEdge(nodeB, nodeA);
        }
    }

    /**
     * Computes a smooth, seed-deterministic scalar potential for a cell.
     * Multi-octave coherent noise generates macroscopic "currents" across the
     * world.
     */
    public static double computePotential(long worldSeed, String dimensionId, int cellX, int cellZ) {
        long dimHash = (long) dimensionId.hashCode() * 0x517cc1b727220a95L;
        long seed = worldSeed ^ dimHash;

        // Continental gentle slope
        double angle = ((seed & 0xFFFF) / 65536.0) * (2.0 * Math.PI);
        double tiltX = Math.cos(angle) * 0.15;
        double tiltZ = Math.sin(angle) * 0.15;
        double baseTilt = cellX * tiltX + cellZ * tiltZ;

        // Multi-octave smooth noise
        double octave1 = sampleNoise(seed, cellX / 16.0, cellZ / 16.0) * 100.0;
        double octave2 = sampleNoise(seed + 1013L, cellX / 8.0, cellZ / 8.0) * 40.0;
        double octave3 = sampleNoise(seed + 2027L, cellX / 4.0, cellZ / 4.0) * 15.0;

        // Local micro-variation for deterministic tie-breaking
        double micro = (LaylineLayoutUtil.compoundSeed(seed, dimensionId, cellX, cellZ) & 0xFFFL) / 4096.0;

        return baseTilt + octave1 + octave2 + octave3 + micro;
    }

    private static double sampleNoise(long seed, double x, double z) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        int x1 = x0 + 1;
        int z1 = z0 + 1;

        double fx = x - x0;
        double fz = z - z0;

        // Smooth cubic hermite curve
        double sx = fx * fx * (3.0 - 2.0 * fx);
        double sz = fz * fz * (3.0 - 2.0 * fz);

        double n00 = hashNoise(seed, x0, z0);
        double n10 = hashNoise(seed, x1, z0);
        double n01 = hashNoise(seed, x0, z1);
        double n11 = hashNoise(seed, x1, z1);

        double nx0 = n00 + sx * (n10 - n00);
        double nx1 = n01 + sx * (n11 - n01);

        return nx0 + sz * (nx1 - nx0);
    }

    private static double hashNoise(long seed, int x, int z) {
        long h = seed + (long) x * 374761393L + (long) z * 668265263L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65536.0;
    }

    private static boolean isLocalPeak(long seed, String dim, int cx, int cz, double myPot) {
        return myPot > computePotential(seed, dim, cx + 1, cz)
                && myPot > computePotential(seed, dim, cx - 1, cz)
                && myPot > computePotential(seed, dim, cx, cz + 1)
                && myPot > computePotential(seed, dim, cx, cz - 1);
    }

    private static boolean isLocalPit(long seed, String dim, int cx, int cz, double myPot) {
        return myPot < computePotential(seed, dim, cx + 1, cz)
                && myPot < computePotential(seed, dim, cx - 1, cz)
                && myPot < computePotential(seed, dim, cx, cz + 1)
                && myPot < computePotential(seed, dim, cx, cz - 1);
    }

    private static boolean isHighestNeighbor(long seed, String dim, int cx, int cz, int nx, int nz) {
        double neighborPot = computePotential(seed, dim, nx, nz);
        int[][] neighbors = { { cx + 1, cz }, { cx - 1, cz }, { cx, cz + 1 }, { cx, cz - 1 } };
        for (int[] pos : neighbors) {
            if (pos[0] == nx && pos[1] == nz)
                continue;
            if (computePotential(seed, dim, pos[0], pos[1]) > neighborPot) {
                return false;
            }
        }
        return true;
    }

    private static boolean isLowestNeighbor(long seed, String dim, int cx, int cz, int nx, int nz) {
        double neighborPot = computePotential(seed, dim, nx, nz);
        int[][] neighbors = { { cx + 1, cz }, { cx - 1, cz }, { cx, cz + 1 }, { cx, cz - 1 } };
        for (int[] pos : neighbors) {
            if (pos[0] == nx && pos[1] == nz)
                continue;
            if (computePotential(seed, dim, pos[0], pos[1]) < neighborPot) {
                return false;
            }
        }
        return true;
    }

    private static boolean isGuaranteedEdgeForNode(long seed, String dim, int x1, int z1, int x2, int z2) {
        // Check if either endpoint would otherwise have 0 edges connected
        return isNodeIsolated(seed, dim, x1, z1, x2, z2) || isNodeIsolated(seed, dim, x2, z2, x1, z1);
    }

    private static boolean isNodeIsolated(long seed, String dim, int cx, int cz, int testX, int testZ) {
        int[][] neighbors = { { cx + 1, cz }, { cx - 1, cz }, { cx, cz + 1 }, { cx, cz - 1 } };
        int lowestX = cx, lowestZ = cz;
        double lowestPot = Double.MAX_VALUE;

        for (int[] n : neighbors) {
            // Check raw roll for this neighbor
            int nx = n[0], nz = n[1];
            int minX = Math.min(cx, nx), minZ = (cx == nx) ? Math.min(cz, nz) : (cx < nx ? cz : nz);
            int maxX = Math.max(cx, nx), maxZ = (cx == nx) ? Math.max(cz, nz) : (cx > nx ? cz : nz);

            long edgeHash = LaylineLayoutUtil.compoundSeed(seed, dim, minX, minZ);
            edgeHash ^= (long) maxX * 83492791L;
            edgeHash = Long.rotateLeft(edgeHash, 17);
            edgeHash ^= (long) maxZ * 53495867L;
            edgeHash ^= edgeHash >>> 33;
            edgeHash *= 0xff51afd7ed558ccdL;
            edgeHash ^= edgeHash >>> 33;

            double roll = ((edgeHash >>> 16) & 0xFFFF) / 65536.0;
            if (roll < 0.75) {
                return false; // Has at least one natural edge, not isolated!
            }

            double pot = computePotential(seed, dim, nx, nz);
            if (pot < lowestPot) {
                lowestPot = pot;
                lowestX = nx;
                lowestZ = nz;
            }
        }

        // If isolated, the edge to the lowest neighbor is forced
        return lowestX == testX && lowestZ == testZ;
    }
}
