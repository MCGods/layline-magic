package com.example.laylines.event;

import com.example.laylines.network.LaylineNode;
import com.example.laylines.network.LaylineNode.AttributeType;
import com.example.laylines.saveddata.LaylinesData;
import com.example.laylines.utils.LaylineGraphUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * World generation listener for automated layline node spawning (README Section
 * 2).
 *
 * Implements a clean, unified database model:
 * - Every visited node is physically instantiated and saved to LaylinesData
 * during chunk generation
 * - Structure-First Rule: Desert Temples get custom anchored nodes with
 * weighted generation
 * - Standard Fallback: Other cells use seed-offset math with standard 50%
 * generation chance
 */
public class LaylineWorldGenListener {

    /** Structure resource key for Desert Temples */
    private static final ResourceKey<Structure> DESERT_PYRAMID_KEY = ResourceKey.create(Registries.STRUCTURE,
            new ResourceLocation("desert_pyramid"));

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        // Only process on server side
        if (event.getLevel().isClientSide()) {
            return;
        }

        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        ChunkAccess chunk = event.getChunk();
        long seed = serverLevel.getSeed();
        String dimensionId = serverLevel.dimension().location().toString();

        // Calculate the chunk's corresponding 256x256 cell index
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;
        int cellX = LaylineGraphUtil.toCell(chunkX * 16); // Chunk is 16 blocks
        int cellZ = LaylineGraphUtil.toCell(chunkZ * 16);

        LaylinesData data = LaylinesData.get(serverLevel.getServer());

        // Check if LaylinesData already contains a node for this cell
        if (nodeExistsInCell(data, cellX, cellZ)) {
            return; // Node already exists, do nothing
        }

        // Look up the Desert Temple structure from the registry
        Optional<Holder.Reference<Structure>> structureHolder = serverLevel.registryAccess()
                .registryOrThrow(Registries.STRUCTURE)
                .getHolder(DESERT_PYRAMID_KEY);

        boolean hasTemple = false;
        BlockPos templePos = null;

        if (structureHolder.isPresent()) {
            // Check if this chunk contains a Desert Temple
            StructureStart structureStart = chunk.getStartForStructure(structureHolder.get().value());
            if (structureStart != null && structureStart.isValid()) {
                // Get the structure's bounding box and calculate center
                templePos = structureStart.getBoundingBox().getCenter();
                if (templePos != null) {
                    hasTemple = true;
                }
            }
        }

        LaylineNode node;

        if (hasTemple) {
            // Structure-First Rule: Create node anchored to temple center
            // Use weighted generation: 90% FIRE, 10% DARK
            AttributeType generatingType = rollDesertTempleType(new Random(seed + cellX * 31L + cellZ));
            BlockPos nodePos = new BlockPos(
                    templePos.getX(),
                    templePos.getY() != 0 ? templePos.getY() : LaylineNode.DEFAULT_Y,
                    templePos.getZ());
            node = new LaylineNode(UUID.randomUUID(), nodePos, generatingType);
        } else {
            // Standard Fallback: Use seed-offset math with standard 50% generation chance
            BlockPos cellPos = LaylineGraphUtil.positionForCell(seed, dimensionId, cellX, cellZ);
            node = new LaylineNode(cellPos);
        }

        // Save to LaylinesData
        data.putNode(node);
    }

    /**
     * Checks if a node already exists within the bounds of a given cell.
     */
    private static boolean nodeExistsInCell(LaylinesData data, int cellX, int cellZ) {
        int cellMinX = cellX * LaylineGraphUtil.CELL_SIZE;
        int cellMinZ = cellZ * LaylineGraphUtil.CELL_SIZE;
        int cellMaxX = cellMinX + LaylineGraphUtil.CELL_SIZE - 1;
        int cellMaxZ = cellMinZ + LaylineGraphUtil.CELL_SIZE - 1;

        for (LaylineNode node : data.getNodes().values()) {
            BlockPos pos = node.getPosition();
            if (pos.getX() >= cellMinX && pos.getX() <= cellMaxX &&
                    pos.getZ() >= cellMinZ && pos.getZ() <= cellMaxZ) {
                return true;
            }
        }
        return false;
    }

    /**
     * Weighted generation for Desert Temple structures:
     * - 90% chance to be FIRE
     * - 10% chance to be DARK
     */
    private static AttributeType rollDesertTempleType(Random random) {
        if (random.nextFloat() < 0.9f) {
            return AttributeType.FIRE;
        }
        return AttributeType.DARK;
    }
}
