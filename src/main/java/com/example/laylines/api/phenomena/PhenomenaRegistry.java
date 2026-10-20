package com.example.laylines.api.phenomena;

import com.example.laylines.ConfigDefaults;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized registry for all Layline phenomena and their active configuration rules.
 *
 * External mods can register custom phenomena here during mod setup.
 * Configuration rules are populated from config (or defaulted) on config load.
 */
public final class PhenomenaRegistry {

    private static final Map<ResourceLocation, LaylinePhenomenon> REGISTRY = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, PhenomenaRule> RULES = new ConcurrentHashMap<>();
    private static volatile List<LaylinePhenomenon> SORTED_PHENOMENA = List.of();

    static {
        // Pre-fill with built-in defaults
        reloadFromConfig(ConfigDefaults.PHENOMENA_RULES_DEFAULTS);
    }

    private PhenomenaRegistry() {
    }

    /**
     * Registers a new phenomenon. If no rule is configured yet, populates with the phenomenon's default rule.
     *
     * @param phenomenon The phenomenon to register
     */
    public static synchronized void register(LaylinePhenomenon phenomenon) {
        if (phenomenon == null) {
            throw new IllegalArgumentException("Cannot register null phenomenon");
        }
        REGISTRY.put(phenomenon.getId(), phenomenon);
        RULES.putIfAbsent(phenomenon.getId(), phenomenon.defaultRule());
        updateSortedList();
    }

    /**
     * Retrieves a phenomenon by its identifier.
     */
    public static Optional<LaylinePhenomenon> get(ResourceLocation id) {
        return Optional.ofNullable(REGISTRY.get(id));
    }

    /**
     * Returns all registered phenomena ordered by priority (highest priority first).
     */
    public static List<LaylinePhenomenon> getAllSorted() {
        return SORTED_PHENOMENA;
    }

    /**
     * Reloads phenomena rules originating from configuration, preserving defaults for unconfigured phenomena.
     */
    public static synchronized void reloadFromConfig(Map<ResourceLocation, PhenomenaRule> configRules) {
        RULES.clear();
        if (configRules != null) {
            RULES.putAll(configRules);
        }
        // Ensure every registered phenomenon has a rule, falling back to defaultRule()
        for (LaylinePhenomenon phenomenon : REGISTRY.values()) {
            RULES.putIfAbsent(phenomenon.getId(), phenomenon.defaultRule());
        }
    }

    /**
     * Gets the active configuration rule for a phenomenon.
     */
    public static PhenomenaRule getRule(LaylinePhenomenon phenomenon) {
        PhenomenaRule rule = RULES.get(phenomenon.getId());
        if (rule != null) {
            return rule;
        }
        return phenomenon.defaultRule();
    }

    /**
     * Gets the active configuration rule for a phenomenon ID.
     */
    public static PhenomenaRule getRule(ResourceLocation id) {
        PhenomenaRule rule = RULES.get(id);
        if (rule != null) {
            return rule;
        }
        LaylinePhenomenon phenomenon = REGISTRY.get(id);
        if (phenomenon != null) {
            return phenomenon.defaultRule();
        }
        return ConfigDefaults.PHENOMENA_RULES_DEFAULTS.get(id);
    }

    private static void updateSortedList() {
        List<LaylinePhenomenon> list = new ArrayList<>(REGISTRY.values());
        list.sort(Comparator.comparingInt(LaylinePhenomenon::getPriority).reversed());
        SORTED_PHENOMENA = Collections.unmodifiableList(list);
    }
}
