package com.example.laylines.event.phenomena;

import com.example.laylines.LaylinesMod;
import com.example.laylines.api.LaylineEdge;
import com.example.laylines.api.LaylineNode;
import com.example.laylines.api.LaylineNode.AttributeType;
import com.example.laylines.api.phenomena.LaylinePhenomenon;
import com.example.laylines.api.phenomena.PhenomenaRule;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Cursed Night: During twilight hours (sunset to midnight) this phenomenon
 * spawns hostile mobs along
 * energized layline edges. Spawned entities receive a Strength effect and are
 * equipped with gold armor
 * that does not drop. Mobs obey normal Minecraft spawning rules (player
 * distance, light level, etc.) and
 * despawn normally when no longer needed.
 */
public class CursedNightPhenomenon extends LaylinePhenomenon {
    public static final ResourceLocation ID = new ResourceLocation(LaylinesMod.MODID, "cursed_night");

    private static final PhenomenaRule DEFAULT_RULE = new PhenomenaRule(
            true,
            Map.of(AttributeType.DARK, 10.0f),
            16.0, 1, 2);

    public static PhenomenaRule getDefaultRule() {
        return DEFAULT_RULE;
    }

    public CursedNightPhenomenon() {
        super(ID, "Cursed Night", 100);
    }

    @Override
    public PhenomenaRule defaultRule() {
        return DEFAULT_RULE;
    }

    @Override
    public boolean canApply(ServerLevel level, LaylineEdge edge, PhenomenaRule rule) {
        long time = level.getDayTime() % 24000L;
        // Sunset (12000) to Midnight (18000)
        return time >= 12000L && time <= 18000L;
    }

    @Override
    public int execute(ServerLevel level, LaylineEdge edge, PhenomenaRule rule, LaylineNode sourceNode) {
        int maxAffordable = rule.calculateMaxAffordable(sourceNode);
        if (maxAffordable <= 0) {
            return 0;
        }
        int spawns = 0;
        // Attempt up to maxAffordable spawns; stop early if we cannot find a valid
        // position.
        for (int i = 0; i < maxAffordable; i++) {
            BlockPos spawnPos = pickRandomSpawnPos(level, edge, rule.range());
            if (spawnPos == null) {
                continue;
            }

            Mob mob = EntityType.ZOMBIE.create(level);
            if (mob == null) {
                continue;
            }
            mob.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, level.random.nextFloat() * 360.0F,
                    0.0F);
            // Apply Strength I for a short duration (6000 ticks 300 seconds).
            mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 6000, 0, false, false, true));
            // Equip gold armor – drop chances set to 0 so they do not drop on death.
            equipGoldArmor(mob);
            // Standard spawn checks – let the game handle light level, player distance,
            // etc.
            if (!mob.checkSpawnRules(level, MobSpawnType.NATURAL)) {
                mob.discard();
                continue;
            }
            level.addFreshEntity(mob);
            spawns++;
        }
        return spawns;
    }

    /**
     * Picks a random position within {@code range} of the edge line segment.
     * Returns {@code null} if a suitable ground position cannot be found.
     */
    private BlockPos pickRandomSpawnPos(ServerLevel level, LaylineEdge edge, double range) {
        BlockPos start = edge.nodeA();
        BlockPos end = edge.nodeB();
        // Random point along the line segment
        double t = level.random.nextDouble();
        double baseX = start.getX() + t * (end.getX() - start.getX());
        double baseZ = start.getZ() + t * (end.getZ() - start.getZ());
        // Random offset within a circle of radius {@code range}
        double angle = level.random.nextDouble() * 2 * Math.PI;
        double distance = level.random.nextDouble() * range;
        int x = (int) Math.round(baseX + Math.cos(angle) * distance);
        int z = (int) Math.round(baseZ + Math.sin(angle) * distance);
        // Find the surface Y coordinate
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        BlockPos pos = new BlockPos(x, y, z);
        // Ensure the block is replaceable (air) and the block below is solid.
        BlockState state = level.getBlockState(pos);
        if (!state.isAir()) {
            return null;
        }
        BlockPos below = pos.below();
        if (!level.getBlockState(below).isSolidRender(level, below)) {
            return null;
        }
        return pos;
    }

    /**
     * Gives the mob a full set of gold armor. Drop chances are set to 0 so the
     * armour does not drop.
     */
    private void equipGoldArmor(Mob mob) {
        ItemStack helmet = new ItemStack(Items.GOLDEN_HELMET);
        ItemStack chest = new ItemStack(Items.GOLDEN_CHESTPLATE);
        ItemStack legs = new ItemStack(Items.GOLDEN_LEGGINGS);
        ItemStack boots = new ItemStack(Items.GOLDEN_BOOTS);
        mob.setItemSlot(EquipmentSlot.HEAD, helmet);
        mob.setItemSlot(EquipmentSlot.CHEST, chest);
        mob.setItemSlot(EquipmentSlot.LEGS, legs);
        mob.setItemSlot(EquipmentSlot.FEET, boots);
        mob.setDropChance(EquipmentSlot.HEAD, 0.0F);
        mob.setDropChance(EquipmentSlot.CHEST, 0.0F);
        mob.setDropChance(EquipmentSlot.LEGS, 0.0F);
        mob.setDropChance(EquipmentSlot.FEET, 0.0F);
    }
}
