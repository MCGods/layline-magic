package com.example.laylines.event.phenomena;

import com.example.laylines.MainConfig;
import com.example.laylines.api.LaylineEdge;
import com.example.laylines.api.LaylineGraphApi;
import com.example.laylines.api.LaylineNode;
import com.example.laylines.api.LaylineNode.AttributeType;
import com.example.laylines.api.phenomena.LaylinePhenomenon;
import com.example.laylines.api.phenomena.PhenomenaRegistry;
import com.example.laylines.api.phenomena.PhenomenaRule;
import com.example.laylines.saveddata.LaylinesData;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;
import java.util.Map;

/**
 * Centralized manager that drives Layline phenomena across the server.
 *
 * Every configured tick interval (default: 2 ticks), picks a sample of loaded edges
 * (default: 4 edges) and evaluates registered phenomena in order of priority.
 * Mana costs are deducted from the source node per target affected.
 */
public class PhenomenaManager {

    private int tickCounter = 0;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer() == null) {
            return;
        }

        int frequency = MainConfig.getPhenomenaEvaluationFrequencyTicks();
        if (++tickCounter < frequency) {
            return;
        }
        tickCounter = 0;

        for (ServerLevel level : event.getServer().getAllLevels()) {
            evaluatePhenomenaOnLevel(level);
        }
    }

    private void evaluatePhenomenaOnLevel(ServerLevel level) {
        List<LaylineEdge> loadedEdges = LaylineGraphApi.getLoadedEdges(level);
        if (loadedEdges.isEmpty()) {
            return;
        }

        int edgeCountToEvaluate = Math.min(MainConfig.getPhenomenaEvaluateEdgeCount(), loadedEdges.size());
        List<LaylinePhenomenon> phenomena = PhenomenaRegistry.getAllSorted();
        if (phenomena.isEmpty()) {
            return;
        }

        for (int i = 0; i < edgeCountToEvaluate; i++) {
            LaylineEdge edge = loadedEdges.get(level.random.nextInt(loadedEdges.size()));
            evaluateEdge(level, edge, phenomena);
        }
    }

    private void evaluateEdge(ServerLevel level, LaylineEdge edge, List<LaylinePhenomenon> phenomena) {
        LaylineNode sourceNode = edge.source();
        boolean stateChanged = false;

        for (LaylinePhenomenon phenomenon : phenomena) {
            if (!phenomenon.isEnabled()) {
                continue;
            }

            PhenomenaRule rule = PhenomenaRegistry.getRule(phenomenon);
            if (!rule.enabled()) {
                continue;
            }

            // Check if source node can afford at least 1 target
            int maxAffordable = rule.calculateMaxAffordable(sourceNode);
            if (maxAffordable <= 0) {
                continue;
            }

            // Check environmental / state conditions
            if (!phenomenon.canApply(level, edge, rule)) {
                continue;
            }

            // Roll chance probability
            if (rule.chance() < 1.0 && level.random.nextDouble() >= rule.chance()) {
                continue;
            }

            // Execute phenomenon
            int affected = phenomenon.execute(level, edge, rule, sourceNode);
            if (affected > 0) {
                // Deduct per-target mana from source node
                for (Map.Entry<AttributeType, Float> entry : rule.cost().entrySet()) {
                    float deduct = entry.getValue() * affected;
                    float current = sourceNode.getCurrentValue(entry.getKey());
                    sourceNode.setCurrentValue(entry.getKey(), Math.max(0.0f, current - deduct));
                }
                stateChanged = true;
            }
        }

        if (stateChanged) {
            LaylinesData.get(level.getServer()).markDirty();
        }
    }
}

