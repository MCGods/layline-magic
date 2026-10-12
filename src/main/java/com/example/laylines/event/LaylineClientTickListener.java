package com.example.laylines.event;

import com.example.laylines.LaylinesMod;
import com.example.laylines.network.LaylineNode.AttributeType;
import com.example.laylines.network.packet.ClientboundLaylineSyncPacket;
import com.example.laylines.network.packet.LaylineParticleCache;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/**
 * Client-side tick listener for Golden Sight particle rendering.
 *
 * Every few ticks, reads the cached edge data from the server packet
 * and spawns colored dust particles along the layline paths with
 * animated flow direction.
 */
@Mod.EventBusSubscriber(modid = LaylinesMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class LaylineClientTickListener {

    private static int tickCounter = 0;
    private static final int TICK_INTERVAL = 2; // Update every 2 ticks (10 times per second)
    private static final int PARTICLES_PER_EDGE = 3; // Number of new particles per edge per update
    private static final float FLOW_SPEED = 0.2f; // Speed of particle flow along the edge

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        // Only process at the end of the tick
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        tickCounter++;
        if (tickCounter < TICK_INTERVAL) {
            return;
        }
        tickCounter = 0;

        // Get cached edges from server
        var edges = LaylineParticleCache.getLocalEdges();
        if (edges.isEmpty()) {
            return;
        }

        // Spawn particles along each edge
        for (ClientboundLaylineSyncPacket.EdgeData edge : edges) {
            spawnEdgeParticles(edge, mc);
        }
    }

    /**
     * Spawns animated dust particles along an edge with elemental coloring.
     * Particles use native velocity to flow from Node A to Node B naturally.
     */
    private static void spawnEdgeParticles(ClientboundLaylineSyncPacket.EdgeData edge, Minecraft mc) {
        BlockPos nodeA = edge.nodeA();
        BlockPos nodeB = edge.nodeB();
        // (Colors can be added to your custom particle pipeline later, Portal provides
        // a purple/magenta default)

        // Calculate vectors from A to B
        double dx = nodeB.getX() - nodeA.getX();
        double dy = nodeB.getY() - nodeA.getY();
        double dz = nodeB.getZ() - nodeA.getZ();

        // Spawn a steady sequence of moving light beads scattered along the segment
        for (int i = 0; i < PARTICLES_PER_EDGE; i++) {
            // Distribute the starting points randomly along the line path
            double t = Math.random();

            // Introduce a moving shift based on the current game tick for a continuous
            // flowing look
            double flowOffset = (mc.level.getGameTime() * 0.02) % 1.0;
            t = (t + flowOffset) % 1.0;

            double x = nodeA.getX() + 0.5 + (dx * t);
            double y = nodeA.getY() + 0.5 + (dy * t);
            double z = nodeA.getZ() + 0.5 + (dz * t);

            // Calculate a gentle forward motion offset vector along the path line
            double flowVectorX = dx * 0.05;
            double flowVectorY = dy * 0.05;
            double flowVectorZ = dz * 0.05;

            // Minecraft Portal particle math: Spawns at (x + flowVector), moves TOWARD (x)
            // By subtracting the vector from the spawn point, it forces it to travel
            // FORWARD along the layline!
            mc.level.addAlwaysVisibleParticle(
                    net.minecraft.core.particles.ParticleTypes.PORTAL,
                    true, // Force render over long distances
                    x - flowVectorX,
                    y - flowVectorY,
                    z - flowVectorZ,
                    flowVectorX,
                    flowVectorY,
                    flowVectorZ);
        }
    }

    /**
     * Returns the red component for the particle color based on attribute type.
     */
    private static float getParticleRed(AttributeType type) {
        return switch (type) {
            case FIRE -> 1.0f; // Red
            case WATER -> 0.0f; // Blue (red=0)
            case EARTH -> 0.0f; // Green (red=0)
            case AIR -> 1.0f; // White/Yellow (red=1)
            case DARK -> 0.5f; // Purple (red=0.5)
            case LIGHT -> 1.0f; // White (red=1)
        };
    }

    /**
     * Returns the green component for the particle color based on attribute type.
     */
    private static float getParticleGreen(AttributeType type) {
        return switch (type) {
            case FIRE -> 0.0f; // Red (green=0)
            case WATER -> 0.0f; // Blue (green=0)
            case EARTH -> 1.0f; // Green (green=1)
            case AIR -> 1.0f; // White/Yellow (green=1)
            case DARK -> 0.0f; // Purple (green=0)
            case LIGHT -> 1.0f; // White (green=1)
        };
    }

    /**
     * Returns the blue component for the particle color based on attribute type.
     */
    private static float getParticleBlue(AttributeType type) {
        return switch (type) {
            case FIRE -> 0.0f; // Red (blue=0)
            case WATER -> 1.0f; // Blue (blue=1)
            case EARTH -> 0.0f; // Green (blue=0)
            case AIR -> 0.5f; // Yellow (blue=0.5)
            case DARK -> 0.5f; // Purple (blue=0.5)
            case LIGHT -> 1.0f; // White (blue=1)
        };
    }
}
