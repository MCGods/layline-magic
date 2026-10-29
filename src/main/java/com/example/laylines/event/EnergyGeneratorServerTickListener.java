package com.example.laylines.event;

import com.example.laylines.api.LaylineGraphApi;
import com.example.laylines.api.LaylineNode;
import com.example.laylines.api.LaylineNode.AttributeType;
import com.example.laylines.MainConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Server-side tick listener that handles energy generation at layline nodes.
 *
 * Every {@link MainConfig#getGenerationFrequency()} ticks it increases the
 * current value of each loaded node's generating attribute by its
 * {@link LaylineNode#getGenerationRate(AttributeType)}. The generation respects
 * the node's capacity limits for each attribute pool.
 *
 * This listener runs at the START phase of the server tick to ensure generation
 * happens before energy flow calculations (which run at the END phase).
 */
public class EnergyGeneratorServerTickListener {
    private static int tickCounter = 0;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        // Only act at START phase (before energy flow)
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        ServerLevel level = event.getServer().overworld();
        if (level == null) {
            return;
        }

        tickCounter++;
        if (tickCounter < MainConfig.getGenerationFrequency()) {
            return;
        }
        tickCounter = 0;

        // Gather all loaded nodes
        var loadedNodes = LaylineGraphApi.getLoadedNodes(level);
        if (loadedNodes.isEmpty()) {
            return;
        }

        // Generate energy for each node
        for (LaylineNode node : loadedNodes) {
            if (!node.isGenerating()) {
                continue; // Non-generating node
            }

            boolean modified = false;
            for (AttributeType generatingType : node.getGeneratingTypes()) {
                float generationRate = node.getGenerationRate(generatingType);
                if (generationRate <= 0.0f) {
                    continue; // No generation for this attribute
                }

                float currentValue = node.getCurrentValue(generatingType);
                float capacity = node.getCapacity(generatingType);

                // Respect capacity limits
                if (currentValue < capacity) {
                    float newValue = Math.min(currentValue + generationRate, capacity);
                    node.setCurrentValue(generatingType, newValue);
                    modified = true;
                }
            }

            if (modified) {
                // Persist changes
                LaylineGraphApi.putNode(level, node);
            }
        }
    }
}
