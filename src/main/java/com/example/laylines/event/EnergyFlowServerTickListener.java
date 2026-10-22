package com.example.laylines.event;

import com.example.laylines.api.LaylineEdge;
import com.example.laylines.api.LaylineNode;
import com.example.laylines.api.LaylineNode.AttributeType;
import com.mojang.logging.LogUtils;
import com.example.laylines.api.LaylineGraphApi;
import com.example.laylines.MainConfig;
import net.minecraft.server.level.ServerLevel;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;

/**
 * Server-side tick listener that handles energy flow between layline nodes.
 *
 * Every {@link MainConfig#getFlowFrequency()} ticks it evaluates a subset of
 * loaded nodes (up to
 * {@link MainConfig#getFlowNodes()}). For each chosen source node it selects a
 * random outgoing edge
 * and transfers up to {@link MainConfig#getFlowRate()} units of energy,
 * distributed across the source's
 * attribute pools. The transfer respects the target node's spare capacity. If
 * the source has all pools
 * at capacity, the flow is split evenly across a random selection of
 * attributes.
 */
public class EnergyFlowServerTickListener {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static int tickCounter = 0;
    private static final Random RANDOM = new Random();

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        // Only act at END phase
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        ServerLevel level = event.getServer().overworld();
        if (level == null) {
            return;
        }

        tickCounter++;
        if (tickCounter < MainConfig.getFlowFrequency()) {
            return;
        }
        tickCounter = 0;

        // Gather all loaded nodes
        Set<LaylineNode> loadedNodes = LaylineGraphApi.getLoadedNodes(level);
        if (loadedNodes.isEmpty()) {
            return;
        }

        // Limit the number of nodes processed this tick
        int nodesToProcess = Math.min(MainConfig.getFlowNodes(), loadedNodes.size());
        List<LaylineNode> nodeList = new ArrayList<>(loadedNodes);
        Collections.shuffle(nodeList, RANDOM);
        List<LaylineNode> selectedSources = nodeList.subList(0, nodesToProcess);

        // Pre-fetch all loaded edges for fast lookup
        List<LaylineEdge> loadedEdges = LaylineGraphApi.getLoadedEdges(level);

        for (LaylineNode source : selectedSources) {
            // Filter outgoing edges where this node is the source
            List<LaylineEdge> outgoing = loadedEdges.stream()
                    .filter(e -> e.source().equals(source))
                    .collect(Collectors.toList());

            if (outgoing.isEmpty()) {
                continue; // No where to send energy from this node
            }

            // Choose a random target edge
            LaylineEdge edge = outgoing.get(RANDOM.nextInt(outgoing.size()));
            LaylineNode target = edge.target();

            // Determine how many units to transfer this tick
            int flowRate = MainConfig.getFlowRate();
            // Build a mutable list of attribute types that can donate energy
            List<AttributeType> donorAttrs = new ArrayList<>();
            for (AttributeType type : AttributeType.values()) {
                if (source.getCurrentValue(type) > 0) {
                    donorAttrs.add(type);
                }
            }
            if (donorAttrs.isEmpty()) {
                continue; // Nothing to give
            }

            // Perform up to flowRate unit transfers
            for (int i = 0; i < flowRate; i++) {
                AttributeType chosenAttr;
                // Weighted random based on current value (more stored energy = higher chance)
                int total = donorAttrs.stream()
                        .mapToInt(t -> (int) source.getCurrentValue(t))
                        .sum();
                if (total == 0) {
                    break; // No energy left to send
                }
                int roll = RANDOM.nextInt(total);
                int cumulative = 0;
                AttributeType selected = donorAttrs.get(0);
                for (AttributeType t : donorAttrs) {
                    cumulative += (int) source.getCurrentValue(t);
                    if (roll < cumulative) {
                        selected = t;
                        break;
                    }
                }
                chosenAttr = selected;

                // Verify target has capacity for this attribute
                if (target.getCurrentValue(chosenAttr) >= target.getCapacity(chosenAttr)) {
                    // No spare capacity – skip this unit
                    continue;
                }

                // Transfer exactly 1 unit (float) of energy
                source.setCurrentValue(chosenAttr, source.getCurrentValue(chosenAttr) - 1.0f);
                target.setCurrentValue(chosenAttr, target.getCurrentValue(chosenAttr) + 1.0f);
                // Persist changes
                LaylineGraphApi.putNode(level, source);
                LaylineGraphApi.putNode(level, target);
            }
        }
    }
}
