package com.example.laylines.event;

import com.example.laylines.Config;
import com.example.laylines.network.LaylineNode;
import com.example.laylines.network.LaylineNode.AttributeType;
import com.example.laylines.saveddata.LaylinesData;
import com.example.laylines.utils.LaylineGraphUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Applies Earth layline growth to randomly sampled crop columns in loaded
 * chunks.
 */
public class MiracleGrowthHandler {
    private static int tickCounter;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Config.isMiracleGrowthEnabled()) {
            return;
        }

        if (++tickCounter < Config.getMiracleGrowthIntervalSeconds()) {
            return;
        }
        tickCounter = 0;

        for (ServerLevel level : event.getServer().getAllLevels()) {
            accelerateCropsAlongEarthEdges(level);
        }
    }

    private static void accelerateCropsAlongEarthEdges(ServerLevel level) {
        LaylinesData data = LaylinesData.get(level.getServer());
        Set<LaylineGraphUtil.LaylineEdge> edges = LaylineGraphUtil.getEdgesForAttribute(
                level.getSeed(), level.dimension().location().toString(), AttributeType.EARTH, data);
        if (edges.isEmpty()) {
            return;
        }

        Map<BlockPos, LaylineNode> nodesByPosition = new HashMap<>();
        for (LaylineNode node : data.getNodes().values()) {
            nodesByPosition.put(node.getPosition(), node);
        }

        double radius = Config.getMiracleGrowthRadius();
        double earthCost = Config.getMiracleGrowthEarthCost();
        int samplesPerEdge = Config.getMiracleGrowthSamplesPerEdge();
        Set<BlockPos> updatedCrops = new HashSet<>();
        boolean consumedEnergy = false;

        for (LaylineGraphUtil.LaylineEdge edge : edges) {
            if (findEarthEnergyNode(edge, nodesByPosition, earthCost, edge.nodeA()) == null) {
                continue;
            }

            for (int sample = 0; sample < samplesPerEdge; sample++) {
                BlockPos samplePos = LaylineGraphUtil.randomPositionNearEdge(edge, radius, level);
                if (LaylineGraphUtil.pointToLineSegmentDistance(samplePos, edge.nodeA(), edge.nodeB()) > radius) {
                    continue;
                }

                LaylineNode energyNode = findEarthEnergyNode(edge, nodesByPosition, earthCost, samplePos);
                if (energyNode == null) {
                    break;
                }

                BlockPos cropPos = findEligibleCropAtColumn(level, samplePos);
                if (cropPos == null || updatedCrops.contains(cropPos)) {
                    continue;
                }

                BlockState state = level.getBlockState(cropPos);
                if (!(state.getBlock() instanceof CropBlock crop)
                        || !crop.isValidBonemealTarget(level, cropPos, state)) {
                    continue;
                }

                crop.performBonemeal(level, level.random, cropPos, state);
                if (earthCost > 0.0) {
                    energyNode.setCurrentValue(AttributeType.EARTH,
                            (float) (energyNode.getCurrentValue(AttributeType.EARTH) - earthCost));
                    consumedEnergy = true;
                }
                updatedCrops.add(cropPos);
            }
        }

        if (consumedEnergy) {
            data.markDirty();
        }
    }

    private static LaylineNode findEarthEnergyNode(LaylineGraphUtil.LaylineEdge edge,
            Map<BlockPos, LaylineNode> nodesByPosition, double earthCost, BlockPos samplePos) {
        LaylineNode nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (BlockPos endpoint : new BlockPos[] { edge.nodeA(), edge.nodeB() }) {
            LaylineNode node = nodesByPosition.get(endpoint);
            if (node == null) {
                continue;
            }

            float energy = node.getCurrentValue(AttributeType.EARTH);
            if (energy <= 0.0f || energy < earthCost) {
                continue;
            }

            double distance = LaylineGraphUtil.horizontalDistance(node.getPosition(), samplePos);
            if (distance < nearestDistance) {
                nearest = node;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private static BlockPos findEligibleCropAtColumn(ServerLevel level, BlockPos samplePos) {
        int chunkX = Math.floorDiv(samplePos.getX(), 16);
        int chunkZ = Math.floorDiv(samplePos.getZ(), 16);
        ChunkAccess chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (chunk == null) {
            return null;
        }

        LevelChunkSection[] sections = chunk.getSections();
        if (sections.length == 0) {
            return null;
        }

        int startSection = level.random.nextInt(sections.length);
        int x = samplePos.getX();
        int z = samplePos.getZ();
        for (int sectionOffset = 0; sectionOffset < sections.length; sectionOffset++) {
            int sectionIndex = (startSection + sectionOffset) % sections.length;
            LevelChunkSection section = sections[sectionIndex];
            if (!section.maybeHas(state -> state.getBlock() instanceof CropBlock)) {
                continue;
            }

            int sectionMinY = level.getMinBuildHeight() + (sectionIndex << 4);
            int startY = level.random.nextInt(16);
            for (int yOffset = 0; yOffset < 16; yOffset++) {
                int y = sectionMinY + (startY + yOffset) % 16;
                BlockPos cropPos = new BlockPos(x, y, z);
                BlockState state = chunk.getBlockState(cropPos);
                if (state.getBlock() instanceof CropBlock crop
                        && crop.isValidBonemealTarget(level, cropPos, state)) {
                    return cropPos;
                }
            }
        }
        return null;
    }
}
