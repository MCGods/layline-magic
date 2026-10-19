package com.example.laylines.event;

import com.example.laylines.LaylinesMod;
import com.example.laylines.api.LaylineEdge;
import com.example.laylines.api.LaylineGraphApi;
import com.example.laylines.api.LaylineNode.AttributeType;
import com.example.laylines.clientdata.ClientLaylineEdge;
import com.example.laylines.network.packet.laylineParticles.ClientboundLaylineSyncPacket;

import net.minecraft.server.level.ServerLevel;
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
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        net.minecraft.core.BlockPos playerPos = player.blockPosition();
        List<LaylineEdge> nearbyEdges = LaylineGraphApi.getEdgesNear(
                serverLevel, playerPos, LaylineGraphApi.DEFAULT_SEARCH_RADIUS, null);

        List<ClientLaylineEdge> edges = new ArrayList<>();
        for (LaylineEdge edge : nearbyEdges) {
            AttributeType type = edge.getGeneratingType() != null ? edge.getGeneratingType() : AttributeType.EARTH;
            edges.add(new ClientLaylineEdge(
                    edge.nodeA(),
                    edge.nodeB(),
                    type,
                    ClientLaylineEdge.scaleEnergy(edge.getCurrentEnergy(type))));
        }

        // Send packet to player
        LaylinesMod.PACKET_HANDLER.send(new ClientboundLaylineSyncPacket(edges),
                PacketDistributor.PLAYER.with(player));
    }
}
