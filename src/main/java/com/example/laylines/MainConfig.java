package com.example.laylines;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;

import com.example.laylines.api.IntRange;
import com.example.laylines.api.LaylineNode;
import com.example.laylines.api.StructureAnchorRegistry;
import com.example.laylines.api.StructureAnchorRule;
import com.mojang.logging.LogUtils;

@Mod.EventBusSubscriber(modid = LaylinesMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class MainConfig {
        private static final Logger LOGGER = LogUtils.getLogger();

        private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

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

        private static final ForgeConfigSpec.ConfigValue<List<? extends com.electronwill.nightconfig.core.Config>> ANCHOR_RULES = BUILDER
                        .comment("Structure anchor rules. Use TOML table syntax, e.g.:\n[[anchor_rules]]\nstructure = \"minecraft:desert_pyramid\"\npriority = 100\n[anchor_rules.odds]\nFIRE = 50\nEARTH = 20\nDARK = 30\n[anchor_rules.caps]\nFIRE = \"100-200\"\nEARTH = \"80-150\"\nDARK = \"60-120\"\n\nAdd additional [[anchor_rules]] tables for other structures.")
                        .defineListAllowEmpty("anchor_rules",
                                        createDefaultAnchorRules(),
                                        obj -> obj instanceof com.electronwill.nightconfig.core.UnmodifiableConfig
                                                        || obj instanceof Map);

        private static final ForgeConfigSpec.BooleanValue MIRACLE_GROWTH_ENABLED = BUILDER
                        .comment("Whether Earth laylines accelerate nearby crops.")
                        .define("miracleGrowth.enabled", true);

        private static final ForgeConfigSpec.IntValue MIRACLE_GROWTH_INTERVAL_TICKS = BUILDER
                        .comment("Seconds between miracle growth pulses.")
                        .defineInRange("miracleGrowth.intervalTicks", 60, 1, 72000);

        private static final ForgeConfigSpec.IntValue MIRACLE_GROWTH_RADIUS = BUILDER
                        .comment("Maximum horizontal distance in blocks from an Earth layline edge.")
                        .defineInRange("miracleGrowth.radius", 10, 1, 64);

        private static final ForgeConfigSpec.DoubleValue MIRACLE_GROWTH_EARTH_COST = BUILDER
                        .comment("Earth energy consumed for each crop accelerated.")
                        .defineInRange("miracleGrowth.earthCostPerCrop", 5.0, 0.0, 100.0);

        private static final ForgeConfigSpec.IntValue MIRACLE_GROWTH_SAMPLES_PER_EDGE = BUILDER
                        .comment("Random crop locations tested along each energized Earth edge per pulse.")
                        .defineInRange("miracleGrowth.samplesPerEdge", 8, 1, 128);

        static final ForgeConfigSpec SPEC = BUILDER.build();

        private static volatile List<StructureAnchorRule> cachedRules = null;

        @SubscribeEvent
        public static void onConfigLoad(final ModConfigEvent event) {
                if (event.getConfig().getSpec() == SPEC) {
                        refreshRules();
                        LOGGER.info("Laylines config loaded. {} anchor rules active.", cachedRules.size());
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

        public static boolean isMiracleGrowthEnabled() {
                return MIRACLE_GROWTH_ENABLED.get();
        }

        public static int getMiracleGrowthIntervalSeconds() {
                return MIRACLE_GROWTH_INTERVAL_TICKS.get() * 20;
        }

        public static int getMiracleGrowthRadius() {
                return MIRACLE_GROWTH_RADIUS.get();
        }

        public static double getMiracleGrowthEarthCost() {
                return MIRACLE_GROWTH_EARTH_COST.get();
        }

        public static int getMiracleGrowthSamplesPerEdge() {
                return MIRACLE_GROWTH_SAMPLES_PER_EDGE.get();
        }

        private static boolean validateItemName(final Object obj) {
                return obj instanceof final String itemName
                                && ForgeRegistries.ITEMS.containsKey(new ResourceLocation(itemName));
        }

}
