package com.example.laylines.event;

import com.example.laylines.LaylinesMod;
import com.example.laylines.api.LaylineGraphApi;
import com.example.laylines.api.StickMode;
import com.example.laylines.clientdata.ClientLaylineEdge;
import com.example.laylines.clientdata.LaylineStickClientCache;
import com.example.laylines.network.LaylineEdge;
import com.example.laylines.network.LaylineNode;
import com.example.laylines.network.packet.locatorStick.ClientboundStickReportPacket;
import com.example.laylines.utils.LaylineGraphUtil;
import com.mojang.logging.LogUtils;

import net.minecraftforge.network.PacketDistributor;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Optional;
import org.slf4j.Logger;

/**
 * Vanilla stick debug tool (README Section 4 / Phase 1).
 *
 * Two operational modes:
 * - READ_NODE (default): calculate closest node pools
 * - READ_EDGE: calculate closest edge distance
 * - DEBUG_PLANT: instantiate and save new nodes to world data
 *
 * Sneak + right-click: toggle between modes
 * Normal right-click: perform action based on current mode
 */
public class LaylineStickListener {

    private static final Logger LOGGER = LogUtils.getLogger();

    // Removed internal StickMode enum; using shared api.StickMode
    // Removed server-side playerModes map; mode is now tracked client‑side via
    // LaylineStickClientCache

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

        // Directly handle the request server‑side
        StickMode mode = StickMode.valueOf(LaylineStickClientCache.getMode().name());
        handleModeRequest(serverPlayer, mode);
        return;
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

        StickMode mode = StickMode.valueOf(LaylineStickClientCache.getMode().name());
        handleModeRequest(serverPlayer, mode);
        return;
    }

    public static void handleModeRequest(ServerPlayer player, StickMode mode) {
        switch (mode) {
            case READ_NODE:
            case READ_EDGE:
                // Both use the read‑only report logic; the packet will include edge data if any
                sendNearestNodeReportReadOnly(player);
                break;
            case DEBUG_PLANT:
                // Plant mode – create a node if needed and then send the report
                sendNearestNodeReportPlant(player);
                break;
            default:
                LOGGER.warn("Unhandled StickMode {} from {}", mode, player.getName().getString());
        }
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

    private static void toggleStickMode(ServerPlayer player) {
        // Cycle through the three modes: READ_NODE -> READ_EDGE -> DEBUG_PLANT ->
        // READ_NODE
        StickMode current = LaylineStickClientCache.getMode();
        StickMode next;
        switch (current) {
            case READ_NODE -> next = StickMode.READ_EDGE;
            case READ_EDGE -> next = StickMode.DEBUG_PLANT;
            default -> next = StickMode.READ_NODE;
        }
        // Update client cache mode (no node/edge data change)
        LaylineStickClientCache.update(null, -1.0, null, -1.0, next);
        // Notify player
        player.displayClientMessage(Component.literal("Layline Stick Mode: " + next.name()), true);
    }

    /**
     * Read-only mode: queries closest node and edge using LaylineGraphApi without
     * mutating data.
     */
    private static void sendNearestNodeReportReadOnly(ServerPlayer player) {
        BlockPos playerPos = player.blockPosition();

        Optional<LaylineNode> closestOpt = LaylineGraphApi.getClosestNode(player.serverLevel(), playerPos);
        LaylineNode closest = closestOpt.get();
        double closestDist = LaylineGraphUtil.horizontalDistance(playerPos, closest.getPosition());

        // Find closest edge
        Optional<LaylineEdge> closestEdgeOpt = LaylineGraphApi.getClosestEdge(player.serverLevel(),
                playerPos);
        double edgeDist = closestEdgeOpt
                .map(edge -> LaylineGraphUtil.pointToLineSegmentDistance(playerPos, edge.nodeA(), edge.nodeB()))
                .orElse(-1.0);

        // Send structured data packet to the client
        ClientLaylineEdge clientEdge = closestEdgeOpt
                .map(edge -> ClientLaylineEdge.fromServer(edge.source(), edge.target()))
                .orElse(null);
        LaylinesMod.PACKET_HANDLER.send(
                new ClientboundStickReportPacket(closest, closestDist, clientEdge, edgeDist,
                        LaylineStickClientCache.getMode()),
                PacketDistributor.PLAYER.with(player));
    }

    /**
     * Debug plant mode: queries closest node using LaylineGraphApi and shows
     * report.
     */
    private static void sendNearestNodeReportPlant(ServerPlayer player) {

        // TODO: implement plant mode logic (create node if needed, then send report)
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
