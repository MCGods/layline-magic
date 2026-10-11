package com.example.laylines.event;

import com.example.laylines.network.LaylineNode;
import com.example.laylines.network.LaylineNode.AttributeType;
import com.example.laylines.saveddata.LaylinesData;
import com.example.laylines.utils.LaylineGraphUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Vanilla stick debug tool (README Section 4 / Phase 1).
 *
 * Right-click: report 2D distance to the nearest stored layline node.
 * Sneak + right-click a block: plant a random node at that block for testing.
 */
public class LaylineStickListener {

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!isServerStickUse(event)) {
            return;
        }

        Player player = event.getEntity();
        // Sneak-clicking a block is handled by {@link #onRightClickBlock}; skip the
        // scan so we do not print "no laylines" in the same click as a spawn.
        if (player.isShiftKeyDown()) {
            return;
        }

        if (!(player instanceof ServerPlayer serverPlayer) || serverPlayer.getServer() == null) {
            return;
        }

        sendNearestNodeReport(serverPlayer);
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!isServerStickUse(event)) {
            return;
        }

        Player player = event.getEntity();
        if (!player.isShiftKeyDown()) {
            return;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || serverPlayer.getServer() == null) {
            return;
        }

        BlockPos clicked = event.getPos();
        LaylineNode node = new LaylineNode(clicked);
        LaylinesData.get(serverPlayer.getServer()).putNode(node);

        String typeLabel = node.getGeneratingType() == null
                ? "none"
                : node.getGeneratingType().name();
        serverPlayer.displayClientMessage(Component.literal(String.format(
                Locale.ROOT,
                "Spawned layline node at %s | generating: %s",
                formatPos(clicked),
                typeLabel)), true);
    }

    private static boolean isServerStickUse(PlayerInteractEvent event) {
        if (event.getLevel().isClientSide()) {
            return false;
        }
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return false;
        }
        return event.getItemStack().is(Items.STICK);
    }

    private static void sendNearestNodeReport(ServerPlayer player) {
        LaylinesData data = LaylinesData.get(player.getServer());
        long seed = player.serverLevel().getSeed();
        String dimensionId = player.level().dimension().location().toString();
        BlockPos playerPos = player.blockPosition();

        LaylineNode closest = LaylineGraphUtil.findClosestNode(seed, dimensionId, playerPos, data);
        if (closest == null) {
            player.displayClientMessage(Component.literal("No laylines detected in this world."), true);
            return;
        }

        double closestDist = LaylineGraphUtil.horizontalDistance(playerPos, closest.getPosition());
        String typeLabel = closest.getGeneratingType() == null
                ? "none"
                : closest.getGeneratingType().name();
        player.displayClientMessage(Component.literal(String.format(Locale.ROOT,
                "Closest Node: %.1f blocks away | Generator: %s",
                closestDist, typeLabel)), true);

        for (String line : formatNodeChatDump(closest)) {
            player.sendSystemMessage(Component.literal(line));
        }
    }

    /** One chat line per field so the dump stays readable in the history. */
    private static List<String> formatNodeChatDump(LaylineNode node) {
        BlockPos pos = node.getPosition();
        AttributeType generating = node.getGeneratingType();
        List<String> lines = new ArrayList<>();
        lines.add("=== Layline Node ===");
        lines.add("UUID: " + node.getId());
        lines.add("Pos: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
        lines.add("Pools:");
        for (AttributeType type : AttributeType.values()) {
            boolean active = type == generating;
            String line = String.format(Locale.ROOT, "%s%s: %.1f / %.1f%s",
                    active ? "* " : "  ",
                    type.name(),
                    node.getCurrentValue(type),
                    node.getCapacity(type),
                    active ? "  [generating]" : "");
            lines.add(line);
        }
        return lines;
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
