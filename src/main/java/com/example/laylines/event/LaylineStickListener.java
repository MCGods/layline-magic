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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Vanilla stick debug tool (README Section 4 / Phase 1).
 *
 * Two operational modes:
 * - READ_ONLY (default): calculate closest node without creating/saving new
 * nodes
 * - DEBUG_PLANT: instantiate and save new nodes to world data
 *
 * Sneak + right-click: toggle between modes
 * Normal right-click: perform action based on current mode
 */
public class LaylineStickListener {

    /** Operational modes for the layline stick */
    public enum StickMode {
        READ_ONLY,
        DEBUG_PLANT
    }

    /** Tracks the current mode for each player by UUID */
    private static final Map<UUID, StickMode> playerModes = new HashMap<>();

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!isServerStickUse(event)) {
            return;
        }

        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer serverPlayer) || serverPlayer.getServer() == null) {
            return;
        }

        // Sneak + right-click toggles mode
        if (player.isShiftKeyDown()) {
            toggleStickMode(serverPlayer);
            return;
        }

        // Normal right-click performs action based on current mode
        StickMode mode = getStickMode(player);
        if (mode == StickMode.READ_ONLY) {
            sendNearestNodeReportReadOnly(serverPlayer);
        } else {
            sendNearestNodeReportPlant(serverPlayer);
        }
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!isServerStickUse(event)) {
            return;
        }

        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer serverPlayer) || serverPlayer.getServer() == null) {
            return;
        }

        // Sneak + right-click toggles mode
        if (player.isShiftKeyDown()) {
            toggleStickMode(serverPlayer);
            return;
        }

        // Normal right-click performs action based on current mode
        StickMode mode = getStickMode(player);
        if (mode == StickMode.DEBUG_PLANT) {
            BlockPos clicked = event.getPos();
            LaylineNode node = new LaylineNode(clicked);
            LaylinesData.get(serverPlayer.getServer()).putNode(node);

            String typeLabel = node.getGeneratingType() == null
                    ? "none"
                    : node.getGeneratingType().name();
            serverPlayer.displayClientMessage(Component.literal(String.format(
                    Locale.ROOT,
                    "[PLANT] Instantiating new node at %s | generating: %s",
                    formatPos(clicked),
                    typeLabel)), true);
        }
        // READ_ONLY mode does nothing on block click
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

    private static StickMode getStickMode(Player player) {
        return playerModes.computeIfAbsent(player.getUUID(), k -> StickMode.READ_ONLY);
    }

    private static void toggleStickMode(ServerPlayer player) {
        StickMode current = getStickMode(player);
        StickMode newMode = current == StickMode.READ_ONLY ? StickMode.DEBUG_PLANT : StickMode.READ_ONLY;
        playerModes.put(player.getUUID(), newMode);
        player.displayClientMessage(Component.literal("Layline Stick Mode: " + newMode.name()), true);
    }

    /**
     * Read-only mode: calculates closest node without creating or saving new nodes.
     * Checks both mathematical cell positions and actual saved nodes from
     * LaylinesData. Also reports distance to the closest layline edge.
     */
    private static void sendNearestNodeReportReadOnly(ServerPlayer player) {
        LaylinesData data = LaylinesData.get(player.getServer());
        long seed = player.serverLevel().getSeed();
        String dimensionId = player.level().dimension().location().toString();
        BlockPos playerPos = player.blockPosition();

        LaylineNode closest = findClosestNodeReadOnly(seed, dimensionId, playerPos, data);
        if (closest == null) {
            player.displayClientMessage(Component.literal("[READ] No laylines detected in this world."), true);
            return;
        }

        double closestDist = LaylineGraphUtil.horizontalDistance(playerPos, closest.getPosition());
        String typeLabel = closest.getGeneratingType() == null
                ? "none"
                : closest.getGeneratingType().name();

        // Find closest edge
        com.example.laylines.utils.LaylineGraphUtil.LaylineEdge closestEdge = LaylineGraphUtil.findClosestEdge(seed,
                dimensionId, playerPos, player.serverLevel());
        double edgeDist = closestEdge != null
                ? LaylineGraphUtil.pointToLineSegmentDistance(playerPos, closestEdge.nodeA(), closestEdge.nodeB())
                : -1;

        String edgeMessage = edgeDist >= 0
                ? String.format(Locale.ROOT, " | Closest Edge: %.1f blocks", edgeDist)
                : " | No edges detected";

        player.displayClientMessage(Component.literal(String.format(Locale.ROOT,
                "[READ] Closest Node: %.1f blocks away | Generator: %s%s",
                closestDist, typeLabel, edgeMessage)), true);

        for (String line : formatNodeChatDump(closest)) {
            player.sendSystemMessage(Component.literal(line));
        }
    }

    /**
     * Debug plant mode: calculates closest node and creates/saves it at the
     * clicked block position.
     * Uses the data-driven approach reading from LaylinesData.
     */
    private static void sendNearestNodeReportPlant(ServerPlayer player) {
        LaylinesData data = LaylinesData.get(player.getServer());
        long seed = player.serverLevel().getSeed();
        String dimensionId = player.level().dimension().location().toString();
        BlockPos playerPos = player.blockPosition();

        LaylineNode closest = LaylineGraphUtil.findClosestNode(seed, dimensionId, playerPos, data);
        if (closest == null) {
            player.displayClientMessage(Component.literal("[PLANT] No laylines detected in this world."), true);
            return;
        }

        double closestDist = LaylineGraphUtil.horizontalDistance(playerPos, closest.getPosition());
        String typeLabel = closest.getGeneratingType() == null
                ? "none"
                : closest.getGeneratingType().name();
        player.displayClientMessage(Component.literal(String.format(Locale.ROOT,
                "[PLANT] Closest Node: %.1f blocks away | Generator: %s",
                closestDist, typeLabel)), true);

        for (String line : formatNodeChatDump(closest)) {
            player.sendSystemMessage(Component.literal(line));
        }
    }

    /**
     * Read-only node lookup using a pure data-driven approach.
     *
     * Iterates over all nodes currently registered in LaylinesData and performs
     * a 2D distance calculation (X/Z only, ignoring Y) to find the true closest
     * node.
     * This approach works correctly even when nodes are moved or carved from their
     * original seed positions (e.g., structure-anchored nodes).
     *
     * Does not create or save new nodes - only reads from existing data.
     */
    private static LaylineNode findClosestNodeReadOnly(long seed, String dimensionId, BlockPos playerPos,
            LaylinesData data) {
        LaylineNode closest = null;
        double closestDist = Double.MAX_VALUE;

        // Iterate over all actual saved nodes from LaylinesData
        for (LaylineNode node : data.getNodes().values()) {
            double dist = LaylineGraphUtil.horizontalDistance(playerPos, node.getPosition());
            if (dist < closestDist) {
                closestDist = dist;
                closest = node;
            }
        }

        return closest;
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
