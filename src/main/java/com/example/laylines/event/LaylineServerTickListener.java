package com.example.laylines.event;

import com.example.laylines.network.LaylineNode.AttributeType;
import com.example.laylines.network.packet.ClientboundLaylineSyncPacket;
import com.example.laylines.utils.LaylineGraphUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side tick listener for Golden Sight synchronization.
 *
 * Every 20 ticks (1 second), checks if players are wearing a Gold Helmet
 * and sends them the local layline edge data for particle rendering.
 */
public class LaylineServerTickListener {

    private static int tickCounter = 0;
    private static final int TICK_INTERVAL = 20; // Update once per second

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        // Only process at the end of the tick
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        tickCounter++;
        if (tickCounter < TICK_INTERVAL) {
            return;
        }
        tickCounter = 0;

        // Get the server instance
        if (event.getServer() == null) {
            return;
        }

        // Process all players
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            // Check if player is wearing a Gold Helmet
            ItemStack helmetItem = player.getInventory().getArmor(3); // Index 3 is helmet
            if (helmetItem.isEmpty() || !helmetItem.is(Items.GOLDEN_HELMET)) {
                continue;
            }

            // Player is wearing Gold Helmet - send edge data
            sendLaylineDataToPlayer(player);
        }
    }

    /**
     * Calculates local 3x3 cell grid edges and sends them to the player.
     */
    private void sendLaylineDataToPlayer(ServerPlayer player) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }

        long seed = serverLevel.getSeed();
        String dimensionId = serverLevel.dimension().location().toString();
        net.minecraft.core.BlockPos playerPos = player.blockPosition();

        int cellX = LaylineGraphUtil.toCell(playerPos.getX());
        int cellZ = LaylineGraphUtil.toCell(playerPos.getZ());

        List<ClientboundLaylineSyncPacket.EdgeData> edges = new ArrayList<>();

        // Generate edges for the 3x3 cell grid surrounding the player
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                List<LaylineGraphUtil.LaylineEdge> cellEdges = LaylineGraphUtil.getEdgesForCell(
                        seed, dimensionId, cellX + dx, cellZ + dz, serverLevel);

                for (LaylineGraphUtil.LaylineEdge edge : cellEdges) {
                    // For now, use WATER as a default attribute type
                    // TODO: Determine actual attribute type from the nodes
                    edges.add(new ClientboundLaylineSyncPacket.EdgeData(
                            edge.nodeA(), edge.nodeB(), AttributeType.EARTH));
                }
            }
        }

        // Send packet to player
        com.example.laylines.LaylinesMod.PACKET_HANDLER.send(new ClientboundLaylineSyncPacket(edges),
                PacketDistributor.PLAYER.with(player));
    }
}
