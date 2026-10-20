package com.example.laylines.event.phenomena;

import com.example.laylines.LaylinesMod;
import com.example.laylines.MainConfig;
import com.example.laylines.api.LaylineEdge;
import com.example.laylines.api.LaylineNode;
import com.example.laylines.api.LaylineNode.AttributeType;
import com.example.laylines.api.phenomena.LaylinePhenomenon;
import com.example.laylines.api.phenomena.PhenomenaRule;
import com.example.laylines.utils.LaylineGraphUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Miracle Growth: Accelerates nearby crops along energized layline edges.
 *
 * Supports vanilla crops and modded crops (e.g. Mystical Agriculture, Pam's
 * HarvestCraft 2,
 * Ars Nouveau) through bonemeal execution and simulated random tick growth.
 */
public class MiracleGrowthPhenomenon extends LaylinePhenomenon {

    public static final ResourceLocation ID = new ResourceLocation(LaylinesMod.MODID, "miracle_growth");

    private static final PhenomenaRule DEFAULT_RULE;

    static {
        Map<AttributeType, Float> cost = new EnumMap<>(AttributeType.class);
        cost.put(AttributeType.EARTH, 5.0f);
        DEFAULT_RULE = new PhenomenaRule(cost, 10.0, 1.0, 8);
    }

    public static PhenomenaRule getDefaultRule() {
        return DEFAULT_RULE;
    }

    public MiracleGrowthPhenomenon() {
        super(ID, "Miracle Growth", 100);
    }

    @Override
    public boolean isEnabled() {
        return MainConfig.isMiracleGrowthEnabled();
    }

    @Override
    public PhenomenaRule defaultRule() {
        return DEFAULT_RULE;
    }

    @Override
    public boolean canApply(ServerLevel level, LaylineEdge edge, PhenomenaRule rule) {
        return true;
    }

    @Override
    public int execute(ServerLevel level, LaylineEdge edge, PhenomenaRule rule, LaylineNode sourceNode) {
        int maxAffordable = rule.calculateMaxAffordable(sourceNode);
        if (maxAffordable <= 0) {
            return 0;
        }

        List<BlockPos> candidateCrops = findEligibleCropsAlongEdge(level, edge, rule.range(), 32);
        if (candidateCrops.isEmpty()) {
            return 0;
        }

        // Shuffle so growth is distributed evenly across crops
        Collections.shuffle(candidateCrops);

        int targetsToGrow = Math.min(candidateCrops.size(), maxAffordable);
        int affected = 0;

        for (int i = 0; i < targetsToGrow; i++) {
            BlockPos cropPos = candidateCrops.get(i);
            BlockState state = level.getBlockState(cropPos);

            if (state.getBlock() instanceof BonemealableBlock bonemealable
                    && bonemealable.isValidBonemealTarget(level, cropPos, state)) {
                bonemealable.performBonemeal(level, level.random, cropPos, state);
                affected++;
            } else if (state.isRandomlyTicking()) {
                state.randomTick(level, cropPos, level.random);
                affected++;
            }
        }

        return affected;
    }

    /**
     * Efficiently scans loaded chunks along the edge segment to find eligible crops
     * within range.
     */
    /**
     * Randomly scans loaded chunks along the edge segment to find up to
     * {@code maxCandidates} eligible crop positions.
     * The method first builds a list of all chunk coordinates intersecting the
     * edge's bounding box, shuffles
     * that list using the world random, and then processes chunks in that random
     * order. Processing stops early
     * once {@code maxCandidates} crops have been collected, avoiding a full
     * deterministic scan of the region.
     */
    private List<BlockPos> findEligibleCropsAlongEdge(ServerLevel level, LaylineEdge edge, double range,
            int maxCandidates) {
        BlockPos start = edge.nodeA();
        BlockPos end = edge.nodeB();

        int minBlockX = (int) Math.floor(Math.min(start.getX(), end.getX()) - range);
        int maxBlockX = (int) Math.ceil(Math.max(start.getX(), end.getX()) + range);
        int minBlockZ = (int) Math.floor(Math.min(start.getZ(), end.getZ()) - range);
        int maxBlockZ = (int) Math.ceil(Math.max(start.getZ(), end.getZ()) + range);

        int minChunkX = minBlockX >> 4;
        int maxChunkX = maxBlockX >> 4;
        int minChunkZ = minBlockZ >> 4;
        int maxChunkZ = maxBlockZ >> 4;

        // Build a list of chunk coordinates that intersect the edge's range.
        List<int[]> chunkCoords = new ArrayList<>();
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                chunkCoords.add(new int[] { cx, cz });
            }
        }
        // Shuffle the chunk order to achieve random sampling.
        Collections.shuffle(chunkCoords);

        List<BlockPos> crops = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();

        for (int[] coord : chunkCoords) {
            int cx = coord[0];
            int cz = coord[1];
            ChunkAccess chunk = level.getChunkSource().getChunkNow(cx, cz);
            if (chunk == null) {
                continue;
            }

            // Quick chunk-level distance check – skip chunks far from the edge.
            BlockPos chunkCenter = new BlockPos((cx << 4) + 8, 0, (cz << 4) + 8);
            if (LaylineGraphUtil.pointToLineSegmentDistance(chunkCenter, start, end) > range + 16.0) {
                continue;
            }

            LevelChunkSection[] sections = chunk.getSections();
            for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
                LevelChunkSection section = sections[sectionIndex];
                if (section == null || section.hasOnlyAir()
                        || !section.maybeHas(MiracleGrowthPhenomenon::isPotentialCrop)) {
                    continue;
                }

                int sectionMinY = level.getMinBuildHeight() + (sectionIndex << 4);
                int chunkBaseX = cx << 4;
                int chunkBaseZ = cz << 4;

                // Generate a list of all block offsets within the chunk section and shuffle for
                // random order
                List<int[]> blockOffsets = new ArrayList<>();
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = 0; y < 16; y++) {
                            blockOffsets.add(new int[] { x, y, z });
                        }
                    }
                }
                Collections.shuffle(blockOffsets);
                for (int[] offset : blockOffsets) {
                    int x = offset[0];
                    int y = offset[1];
                    int z = offset[2];
                    int worldX = chunkBaseX + x;
                    int worldZ = chunkBaseZ + z;
                    BlockPos horizontalProbe = new BlockPos(worldX, 0, worldZ);
                    if (LaylineGraphUtil.pointToLineSegmentDistance(horizontalProbe, start, end) > range) {
                        continue;
                    }
                    int worldY = sectionMinY + y;
                    BlockPos pos = new BlockPos(worldX, worldY, worldZ);
                    if (!visited.add(pos)) {
                        continue;
                    }
                    BlockState state = section.getBlockState(x, y, z);
                    if (isEligibleCrop(level, pos, state)) {
                        crops.add(pos);
                        if (crops.size() >= maxCandidates) {
                            return crops;
                        }
                    }
                }
            }
        }

        return crops;
    }

    /**
     * Fast palette check to determine if a chunk section may contain crops.
     */
    public static boolean isPotentialCrop(BlockState state) {
        return state.getBlock() instanceof CropBlock
                || state.is(BlockTags.CROPS)
                || state.getBlock() instanceof BonemealableBlock;
    }

    /**
     * Determines whether a specific block is a valid crop target for miracle
     * growth.
     */
    public static boolean isEligibleCrop(ServerLevel level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof CropBlock || state.is(BlockTags.CROPS))) {
            return false;
        }

        if (state.getBlock() instanceof BonemealableBlock bonemealable) {
            return bonemealable.isValidBonemealTarget(level, pos, state);
        }

        return state.isRandomlyTicking();
    }
}
