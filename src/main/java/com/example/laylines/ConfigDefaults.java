package com.example.laylines;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.example.laylines.api.IntRange;
import com.example.laylines.api.LaylineNode;
import com.example.laylines.api.StructureAnchorRule;
import com.example.laylines.api.phenomena.PhenomenaRule;
import com.example.laylines.event.phenomena.CursedNightPhenomenon;
import com.example.laylines.event.phenomena.MiracleGrowthPhenomenon;

import net.minecraft.resources.ResourceLocation;

public class ConfigDefaults {
        public static final List<StructureAnchorRule> ANCHOR_RULES_DEFAULTS = List.of(
                        new StructureAnchorRule(
                                        StructureAnchorRule.keyFromId("minecraft:desert_pyramid"),
                                        100,
                                        Map.of(
                                                        LaylineNode.AttributeType.FIRE, 50,
                                                        LaylineNode.AttributeType.EARTH, 20,
                                                        LaylineNode.AttributeType.DARK, 30),
                                        Map.of(
                                                        LaylineNode.AttributeType.FIRE, new IntRange(100, 200),
                                                        LaylineNode.AttributeType.EARTH, new IntRange(80, 150),
                                                        LaylineNode.AttributeType.DARK, new IntRange(60, 120))));

        public static final Map<ResourceLocation, PhenomenaRule> PHENOMENA_RULES_DEFAULTS = Map.of(
                        MiracleGrowthPhenomenon.ID, MiracleGrowthPhenomenon.getDefaultRule(),
                        CursedNightPhenomenon.ID, CursedNightPhenomenon.getDefaultRule());
}
