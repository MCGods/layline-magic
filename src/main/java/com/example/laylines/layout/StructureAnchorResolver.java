package com.example.laylines.layout;

import com.example.laylines.api.IntRange;
import com.example.laylines.api.LaylineNode;
import com.example.laylines.api.LaylineNode.AttributeType;
import com.example.laylines.api.StructureAnchorRegistry;
import com.example.laylines.api.StructureAnchorRule;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Resolves which registered structure, if any, should act as the layline anchor
 * for a given cell. The resolution is deterministic: it uses deterministic
 * structure
 * placement queries so resolution does not depend on which chunk in the cell
 * loaded first.
 */
public final class StructureAnchorResolver {
    private StructureAnchorResolver() {
    }

    /**
     * Resolve an anchor for the cell at {@code cellX}/{@code cellZ}.
     * Returns a {@link LaylineNode} wrapped in {@link Optional} if a structure
     * start
     * within the cell is found, respecting rule priority.
     */
    public static Optional<LaylineNode> resolve(int cellX, int cellZ, ServerLevel level) {
        int startChunkX = LaylineLayoutUtil.cellMinChunk(cellX);
        int endChunkX = LaylineLayoutUtil.cellMaxChunk(cellX);
        int startChunkZ = LaylineLayoutUtil.cellMinChunk(cellZ);
        int endChunkZ = LaylineLayoutUtil.cellMaxChunk(cellZ);

        int cellCenterX = LaylineLayoutUtil.cellMinBlock(cellX) + LaylineLayoutUtil.CELL_SIZE / 2;
        int cellCenterZ = LaylineLayoutUtil.cellMinBlock(cellZ) + LaylineLayoutUtil.CELL_SIZE / 2;

        ChunkGeneratorStructureState structureState = level.getChunkSource().getGeneratorState();

        for (StructureAnchorRule rule : StructureAnchorRegistry.getAll()) {
            ResourceKey<Structure> key = rule.structureKey();
            Holder<Structure> holder = level.registryAccess()
                    .registryOrThrow(Registries.STRUCTURE)
                    .getHolder(key)
                    .orElse(null);
            if (holder == null)
                continue;
            Structure struct = holder.value();

            List<StructurePlacement> placements = structureState.getPlacementsForStructure(holder);
            if (placements.isEmpty())
                continue;

            StructureStart bestStart = null;
            ChunkPos bestChunkPos = null;
            double bestDistSq = Double.MAX_VALUE;

            for (int cx = startChunkX; cx <= endChunkX; cx++) {
                for (int cz = startChunkZ; cz <= endChunkZ; cz++) {
                    boolean isPlacement = false;
                    for (StructurePlacement placement : placements) {
                        if (placement.isStructureChunk(structureState, cx, cz)) {
                            isPlacement = true;
                            break;
                        }
                    }
                    if (!isPlacement)
                        continue;

                    ChunkPos chunkPos = new ChunkPos(cx, cz);
                    StructureStart start = getOrGenerateStructureStart(level, struct, chunkPos);
                    if (start != null && start.isValid()) {
                        BlockPos center = start.getBoundingBox().getCenter();
                        double dx = center.getX() - cellCenterX;
                        double dz = center.getZ() - cellCenterZ;
                        double distSq = dx * dx + dz * dz;
                        if (distSq < bestDistSq) {
                            bestDistSq = distSq;
                            bestStart = start;
                            bestChunkPos = chunkPos;
                        }
                    }
                }
            }

            if (bestStart != null) {
                return Optional.of(createAnchorNode(bestStart, bestChunkPos, rule, level));
            }
        }

        return Optional.empty();
    }

    private static StructureStart getOrGenerateStructureStart(ServerLevel level, Structure struct, ChunkPos chunkPos) {
        BlockPos checkPos = chunkPos.getWorldPosition();
        StructureStart loaded = level.structureManager().getStructureAt(checkPos, struct);
        if (loaded != null && loaded.isValid()) {
            return loaded;
        }

        return struct.generate(
                level.registryAccess(),
                level.getChunkSource().getGenerator(),
                level.getChunkSource().getGenerator().getBiomeSource(),
                level.getChunkSource().randomState(),
                level.getServer().getStructureManager(),
                level.getSeed(),
                chunkPos,
                0,
                level,
                struct.biomes()::contains);
    }

    private static LaylineNode createAnchorNode(
            StructureStart start,
            ChunkPos chunkPos,
            StructureAnchorRule rule,
            ServerLevel level) {

        long seed = LaylineLayoutUtil.compoundSeed(
                level.getSeed(),
                level.dimension().location().toString(),
                chunkPos.x,
                chunkPos.z);
        Random rng = new Random(seed);

        int totalWeight = rule.generationOdds().values().stream().mapToInt(Integer::intValue).sum();
        int roll = rng.nextInt(totalWeight);
        AttributeType chosen = null;
        int cumulative = 0;
        for (Map.Entry<AttributeType, Integer> e : rule.generationOdds().entrySet()) {
            cumulative += e.getValue();
            if (roll < cumulative) {
                chosen = e.getKey();
                break;
            }
        }
        if (chosen == null) {
            chosen = rule.generationOdds().keySet().iterator().next();
        }

        BlockPos anchorPos = start.getBoundingBox().getCenter();
        BlockPos nodePos = new BlockPos(anchorPos.getX(), LaylineNode.DEFAULT_Y, anchorPos.getZ());
        UUID nodeId = new UUID(seed, ~seed);

        LaylineNode node = new LaylineNode(nodeId, nodePos, chosen);

        for (Map.Entry<AttributeType, IntRange> entry : rule.capacityRanges().entrySet()) {
            node.setCapacity(entry.getKey(), entry.getValue().random(rng));
        }

        return node;
    }
}
