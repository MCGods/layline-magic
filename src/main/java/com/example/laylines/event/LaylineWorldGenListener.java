package com.example.laylines.event;

import com.example.laylines.api.LaylineNode;
import com.example.laylines.layout.LaylineLayoutUtil;
import com.example.laylines.layout.StructureAnchorResolver;
import com.example.laylines.saveddata.LaylinesData;
import com.example.laylines.utils.LaylineGraphUtil;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Optional;

/**
 * World generation listener for automated layline node spawning.
 *
 * Implements a clean, unified database model:
 * - Every visited node is physically instantiated and saved to LaylinesData
 * during chunk generation
 * - Structure-First Rule: Registered structure anchors claim node generation
 * within the cell based on rule priority
 * - Standard Fallback: Other cells use seed-offset math with standard 50%
 * generation chance
 */
public class LaylineWorldGenListener {

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        // Only process for new chunks. This can only be true server side
        if (!event.isNewChunk()) {
            return;
        }

        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        ChunkAccess chunk = event.getChunk();
        long seed = serverLevel.getSeed();
        String dimensionId = serverLevel.dimension().location().toString();

        // Calculate the chunk's corresponding cell index
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;
        int cellX = LaylineLayoutUtil.chunkToCell(chunkX);
        int cellZ = LaylineLayoutUtil.chunkToCell(chunkZ);

        LaylinesData data = LaylinesData.get(serverLevel.getServer());

        // Fast O(1) check if LaylinesData already contains a node for this cell
        if (data.hasNodeInCell(cellX, cellZ)) {
            return; // Node already exists, do nothing
        }

        // Resolve any registered structure anchor for this cell using the extensible
        // framework. The resolver returns a fully‑initialized LaylineNode if a
        // structure anchor is found.
        Optional<LaylineNode> nodeOpt = StructureAnchorResolver.resolve(cellX, cellZ, serverLevel);
        LaylineNode node;
        if (nodeOpt.isPresent()) {
            node = nodeOpt.get();
        } else {
            // Fallback: standard seed‑offset deterministic node
            node = LaylineGraphUtil.createDeterministicNode(seed, dimensionId, cellX, cellZ);
        }

        // Save to LaylinesData
        data.putNode(node);
    }
}
