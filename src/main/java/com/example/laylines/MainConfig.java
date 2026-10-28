package com.example.laylines;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.ForgeRegistries;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.UnmodifiableConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;

import com.example.laylines.api.IntRange;
import com.example.laylines.api.LaylineNode;
import com.example.laylines.api.StructureAnchorRegistry;
import com.example.laylines.api.StructureAnchorRule;
import com.example.laylines.api.phenomena.PhenomenaRegistry;
import com.example.laylines.api.phenomena.PhenomenaRule;
import com.mojang.logging.LogUtils;

@Mod.EventBusSubscriber(modid = LaylinesMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class MainConfig {
        private static final Logger LOGGER = LogUtils.getLogger();

        private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

        private static final ForgeConfigSpec.IntValue FLOW_FREQUENCY = BUILDER
                        .comment("Tick frequency to evaluate energy flow (default: 2000 ticks).")
                        .defineInRange("flow.frequency", 2000, 1, 72000);

        private static final ForgeConfigSpec.IntValue FLOW_RATE = BUILDER
                        .comment("How much energy a node can output (default: 2). This packet of all attributes is sent to a single node.")
                        .defineInRange("flow.rate", 2, 1, 10);

        private static final ForgeConfigSpec.IntValue FLOW_NODES = BUILDER
                        .comment("How many nodes can be flowing at once (default: 10).")
                        .defineInRange("flow.nodes", 10, 1, 100);

        private static final ForgeConfigSpec.IntValue GENERATION_FREQUENCY = BUILDER
                        .comment("Tick frequency to evaluate energy generation (default: 20 ticks).")
                        .defineInRange("generation.frequency", 20, 1, 72000);

        private static final ForgeConfigSpec.IntValue LAYLINE_RENDER_QUALITY = BUILDER
                        .comment("Layline render quality - number of segments per line strand (default: 10). Range: 10-50. Higher values = smoother lines but more GPU load.")
                        .defineInRange("client.laylineRenderQuality", 10, 10, 50);

        private static List<? extends Config> createDefaultAnchorRules() {
                List<Config> list = new ArrayList<>();
                for (StructureAnchorRule rule : ConfigDefaults.ANCHOR_RULES_DEFAULTS) {
                        Config table = Config.inMemory();
                        table.set("structure", rule.structureKey().location().toString());
                        table.set("priority", rule.priority());

                        Config odds = Config.inMemory();
                        rule.generationOdds().forEach((attr, weight) -> odds.set(attr.name(), weight));
                        table.set("odds", odds);

                        Config caps = Config.inMemory();
                        rule.capacityRanges().forEach(
                                        (attr, range) -> caps.set(attr.name(), range.min() + "-" + range.max()));
                        table.set("caps", caps);

                        list.add(table);
                }
                return list;
        }

        private static final ForgeConfigSpec.ConfigValue<List<? extends Config>> ANCHOR_RULES = BUILDER
                        .comment("Structure anchor rules. Use TOML table syntax, e.g.:\n[[anchor_rules]]\nstructure = \"minecraft:desert_pyramid\"\npriority = 100\n[anchor_rules.odds]\nFIRE = 50\nEARTH = 20\nDARK = 30\n[anchor_rules.caps]\nFIRE = \"100-200\"\nEARTH = \"80-150\"\nDARK = \"60-120\"\n\nAdd additional [[anchor_rules]] tables for other structures.")
                        .defineListAllowEmpty("anchor_rules",
                                        createDefaultAnchorRules(),
                                        obj -> obj instanceof UnmodifiableConfig
                                                        || obj instanceof Map);

        // General Phenomena Config
        private static final ForgeConfigSpec.IntValue PHENOMENA_EVALUATION_FREQUENCY = BUILDER
                        .comment("Tick frequency to evaluate phenomena on loaded edges (default: 2 ticks).")
                        .defineInRange("phenomena.evaluationFrequencyTicks", 2, 1, 72000);

        private static final ForgeConfigSpec.IntValue PHENOMENA_EVALUATE_EDGE_COUNT = BUILDER
                        .comment("Number of random loaded edges picked for evaluation each interval (default: 4).")
                        .defineInRange("phenomena.evaluateEdgeCount", 4, 1, 64);

        private static final ForgeConfigSpec.BooleanValue MIRACLE_GROWTH_ENABLED = BUILDER
                        .comment("Whether Miracle Growth phenomenon is enabled.")
                        .define("phenomena.miracleGrowth.enabled", true);
        private static final ForgeConfigSpec.BooleanValue CURSED_NIGHT_ENABLED = BUILDER
                        .comment("Whether Cursed Night phenomenon is enabled.")
                        .define("phenomena.cursedNight.enabled", true);

        private static List<? extends Config> createDefaultPhenomenaRules() {
                List<Config> list = new ArrayList<>();
                for (Map.Entry<ResourceLocation, PhenomenaRule> entry : ConfigDefaults.PHENOMENA_RULES_DEFAULTS
                                .entrySet()) {
                        Config table = Config.inMemory();
                        table.set("phenomenon", entry.getKey().toString());
                        table.set("range", entry.getValue().range());
                        table.set("chance", entry.getValue().chance());
                        table.set("strength", entry.getValue().strength());

                        Config cost = Config.inMemory();
                        entry.getValue().cost().forEach((attr, weight) -> cost.set(attr.name(), weight.doubleValue()));
                        table.set("cost", cost);

                        list.add(table);
                }
                return list;
        }

        private static final ForgeConfigSpec.ConfigValue<List<? extends Config>> PHENOMENA_RULES = BUILDER
                        .comment("Phenomena rules. Use TOML table syntax, e.g.:\n[[phenomena_rules]]\nphenomenon = \"laylines:miracle_growth\"\nrange = 10.0\nchance = 1.0\nstrength = 8\n[phenomena_rules.cost]\nEARTH = 5.0\n\nAdd additional [[phenomena_rules]] tables for other phenomena.")
                        .defineListAllowEmpty("phenomena_rules",
                                        createDefaultPhenomenaRules(),
                                        obj -> obj instanceof UnmodifiableConfig || obj instanceof Map);

        static final ForgeConfigSpec SPEC = BUILDER.build();

        private static volatile List<StructureAnchorRule> cachedRules = null;
        private static volatile Map<ResourceLocation, PhenomenaRule> cachedPhenomenaRules = null;

        @SubscribeEvent
        public static void onConfigLoad(final ModConfigEvent event) {
                if (event.getConfig().getSpec() == SPEC) {
                        refreshRules();
                        LOGGER.info("Laylines config loaded. {} anchor rules, {} phenomena rules active.",
                                        cachedRules.size(), cachedPhenomenaRules.size());
                }
        }

        public static List<StructureAnchorRule> getAnchorRules() {
                if (cachedRules == null) {
                        refreshRules();
                }
                return cachedRules;
        }

        public static synchronized void refreshRules() {
                List<StructureAnchorRule> rules = new ArrayList<>();
                try {
                        List<? extends Config> rawList = ANCHOR_RULES.get();
                        if (rawList != null) {
                                for (Object obj : rawList) {
                                        StructureAnchorRule rule = parseRule(obj);
                                        if (rule != null) {
                                                rules.add(rule);
                                        }
                                }
                        }
                } catch (Exception e) {
                        // Config spec may not be loaded yet
                }

                if (rules.isEmpty()) {
                        cachedRules = ConfigDefaults.ANCHOR_RULES_DEFAULTS;
                } else {
                        cachedRules = Collections.unmodifiableList(rules);
                }
                StructureAnchorRegistry.reloadFromConfig(cachedRules);
                refreshPhenomenaRules();
        }

        public static Map<ResourceLocation, PhenomenaRule> getPhenomenaRules() {
                if (cachedPhenomenaRules == null) {
                        refreshPhenomenaRules();
                }
                return cachedPhenomenaRules;
        }

        public static int getLaylineRenderQuality() {
                return LAYLINE_RENDER_QUALITY.get();
        }

        public static synchronized void refreshPhenomenaRules() {
                Map<ResourceLocation, PhenomenaRule> rules = new HashMap<>();
                try {
                        List<? extends Config> rawList = PHENOMENA_RULES.get();
                        if (rawList != null) {
                                for (Object obj : rawList) {
                                        Map.Entry<ResourceLocation, PhenomenaRule> entry = parsePhenomenaRule(obj);
                                        if (entry != null) {
                                                rules.put(entry.getKey(), entry.getValue());
                                        }
                                }
                        }
                } catch (Exception e) {
                        // Config spec may not be loaded yet
                }

                if (rules.isEmpty()) {
                        cachedPhenomenaRules = ConfigDefaults.PHENOMENA_RULES_DEFAULTS;
                } else {
                        cachedPhenomenaRules = Collections.unmodifiableMap(rules);
                }
                PhenomenaRegistry.reloadFromConfig(cachedPhenomenaRules);
        }

        private static StructureAnchorRule parseRule(Object obj) {
                if (obj instanceof UnmodifiableConfig table) {
                        String structureStr = table.get("structure");
                        if (structureStr == null || structureStr.isBlank()) {
                                return null;
                        }
                        Number priorityNum = table.get("priority");
                        int priority = priorityNum != null ? priorityNum.intValue() : 0;

                        Map<LaylineNode.AttributeType, Integer> odds = new EnumMap<>(LaylineNode.AttributeType.class);
                        Object rawOdds = table.get("odds");
                        if (rawOdds instanceof UnmodifiableConfig oddsConfig) {
                                for (UnmodifiableConfig.Entry entry : oddsConfig
                                                .entrySet()) {
                                        try {
                                                LaylineNode.AttributeType type = LaylineNode.AttributeType
                                                                .valueOf(entry.getKey().toUpperCase(Locale.ROOT));
                                                if (entry.getValue() instanceof Number n) {
                                                        odds.put(type, n.intValue());
                                                }
                                        } catch (IllegalArgumentException ignored) {
                                        }
                                }
                        }

                        Map<LaylineNode.AttributeType, IntRange> caps = new EnumMap<>(LaylineNode.AttributeType.class);
                        Object rawCaps = table.get("caps");
                        if (rawCaps instanceof UnmodifiableConfig capsConfig) {
                                for (UnmodifiableConfig.Entry entry : capsConfig
                                                .entrySet()) {
                                        try {
                                                LaylineNode.AttributeType type = LaylineNode.AttributeType
                                                                .valueOf(entry.getKey().toUpperCase(Locale.ROOT));
                                                if (entry.getValue() instanceof String rangeStr) {
                                                        caps.put(type, IntRange.fromString(rangeStr));
                                                } else if (entry.getValue() instanceof Number n) {
                                                        caps.put(type, new IntRange(n.intValue(), n.intValue()));
                                                }
                                        } catch (Exception ignored) {
                                        }
                                }
                        }

                        if (odds.isEmpty()) {
                                return null;
                        }
                        return new StructureAnchorRule(StructureAnchorRule.keyFromId(structureStr), priority, odds,
                                        caps);
                } else if (obj instanceof Map<?, ?> map) {
                        Object structureVal = map.get("structure");
                        if (structureVal == null)
                                return null;
                        String structureStr = structureVal.toString();
                        Object priorityVal = map.get("priority");
                        int priority = priorityVal instanceof Number n ? n.intValue() : 0;

                        Map<LaylineNode.AttributeType, Integer> odds = new EnumMap<>(LaylineNode.AttributeType.class);
                        Object rawOdds = map.get("odds");
                        if (rawOdds instanceof Map<?, ?> oddsMap) {
                                for (Map.Entry<?, ?> entry : oddsMap.entrySet()) {
                                        try {
                                                LaylineNode.AttributeType type = LaylineNode.AttributeType.valueOf(
                                                                entry.getKey().toString().toUpperCase(Locale.ROOT));
                                                if (entry.getValue() instanceof Number n) {
                                                        odds.put(type, n.intValue());
                                                }
                                        } catch (IllegalArgumentException ignored) {
                                        }
                                }
                        }

                        Map<LaylineNode.AttributeType, IntRange> caps = new EnumMap<>(LaylineNode.AttributeType.class);
                        Object rawCaps = map.get("caps");
                        if (rawCaps instanceof Map<?, ?> capsMap) {
                                for (Map.Entry<?, ?> entry : capsMap.entrySet()) {
                                        try {
                                                LaylineNode.AttributeType type = LaylineNode.AttributeType.valueOf(
                                                                entry.getKey().toString().toUpperCase(Locale.ROOT));
                                                if (entry.getValue() instanceof String rangeStr) {
                                                        caps.put(type, IntRange.fromString(rangeStr));
                                                } else if (entry.getValue() instanceof Number n) {
                                                        caps.put(type, new IntRange(n.intValue(), n.intValue()));
                                                }
                                        } catch (Exception ignored) {
                                        }
                                }
                        }

                        if (odds.isEmpty()) {
                                return null;
                        }
                        return new StructureAnchorRule(StructureAnchorRule.keyFromId(structureStr), priority, odds,
                                        caps);
                }
                return null;
        }

        private static Map.Entry<ResourceLocation, PhenomenaRule> parsePhenomenaRule(Object obj) {
                if (obj instanceof UnmodifiableConfig table) {
                        String phenomStr = table.get("phenomenon");
                        if (phenomStr == null || phenomStr.isBlank()) {
                                return null;
                        }
                        ResourceLocation id = new ResourceLocation(phenomStr);
                        Number rangeNum = table.get("range");
                        double range = rangeNum != null ? rangeNum.doubleValue() : 10.0;
                        Number chanceNum = table.get("chance");
                        double chance = chanceNum != null ? chanceNum.doubleValue() : 1.0;
                        Number strengthNum = table.get("strength");
                        int strength = strengthNum != null ? strengthNum.intValue() : 8;

                        Map<LaylineNode.AttributeType, Float> cost = new EnumMap<>(LaylineNode.AttributeType.class);
                        Object rawCost = table.get("cost");
                        if (rawCost instanceof UnmodifiableConfig costConfig) {
                                for (UnmodifiableConfig.Entry entry : costConfig.entrySet()) {
                                        try {
                                                LaylineNode.AttributeType type = LaylineNode.AttributeType
                                                                .valueOf(entry.getKey().toUpperCase(Locale.ROOT));
                                                if (entry.getValue() instanceof Number n) {
                                                        cost.put(type, n.floatValue());
                                                }
                                        } catch (IllegalArgumentException ignored) {
                                        }
                                }
                        }

                        return Map.entry(id, new PhenomenaRule(cost, range, chance, strength));
                } else if (obj instanceof Map<?, ?> map) {
                        Object phenomVal = map.get("phenomenon");
                        if (phenomVal == null)
                                return null;
                        ResourceLocation id = new ResourceLocation(phenomVal.toString());

                        Object rangeVal = map.get("range");
                        double range = rangeVal instanceof Number n ? n.doubleValue() : 10.0;
                        Object chanceVal = map.get("chance");
                        double chance = chanceVal instanceof Number n ? n.doubleValue() : 1.0;
                        Object strengthVal = map.get("strength");
                        int strength = strengthVal instanceof Number n ? n.intValue() : 8;

                        Map<LaylineNode.AttributeType, Float> cost = new EnumMap<>(LaylineNode.AttributeType.class);
                        Object rawCost = map.get("cost");
                        if (rawCost instanceof Map<?, ?> costMap) {
                                for (Map.Entry<?, ?> entry : costMap.entrySet()) {
                                        try {
                                                LaylineNode.AttributeType type = LaylineNode.AttributeType.valueOf(
                                                                entry.getKey().toString().toUpperCase(Locale.ROOT));
                                                if (entry.getValue() instanceof Number n) {
                                                        cost.put(type, n.floatValue());
                                                }
                                        } catch (IllegalArgumentException ignored) {
                                        }
                                }
                        }

                        return Map.entry(id, new PhenomenaRule(cost, range, chance, strength));
                }
                return null;
        }

        public static int getFlowFrequency() {
                return FLOW_FREQUENCY.get();
        }

        public static int getFlowRate() {
                return FLOW_RATE.get();
        }

        public static int getFlowNodes() {
                return FLOW_NODES.get();
        }

        public static int getGenerationFrequency() {
                return GENERATION_FREQUENCY.get();
        }

        public static int getPhenomenaEvaluationFrequencyTicks() {
                return PHENOMENA_EVALUATION_FREQUENCY.get();
        }

        public static int getPhenomenaEvaluateEdgeCount() {
                return PHENOMENA_EVALUATE_EDGE_COUNT.get();
        }

        public static boolean isMiracleGrowthEnabled() {
                return MIRACLE_GROWTH_ENABLED.get();
        }

        private static boolean validateItemName(final Object obj) {
                return obj instanceof final String itemName
                                && ForgeRegistries.ITEMS.containsKey(new ResourceLocation(itemName));
        }

        public static boolean isCursedNightEnabled() {
                return CURSED_NIGHT_ENABLED.get();
        }

}
