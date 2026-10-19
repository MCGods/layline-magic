package com.example.laylines.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.example.laylines.ConfigDefaults;

/**
 * Central registry for {@link StructureAnchorRule}s. Other mods can add their own rules at
 * runtime via {@link #register(StructureAnchorRule)}.
 */
public final class StructureAnchorRegistry {
    private static final List<StructureAnchorRule> MOD_RULES = new CopyOnWriteArrayList<>();
    private static final List<StructureAnchorRule> CONFIG_RULES = new CopyOnWriteArrayList<>();
    private static volatile List<StructureAnchorRule> ALL_RULES = List.of();

    static {
        // Initialize with built-in defaults
        reloadFromConfig(ConfigDefaults.ANCHOR_RULES_DEFAULTS);
    }

    private StructureAnchorRegistry() {}

    /** Register a new programmatic rule. Rules are kept sorted by descending priority. */
    public static synchronized void register(StructureAnchorRule rule) {
        MOD_RULES.add(rule);
        rebuildAll();
    }

    /**
     * Reload rules originating from configuration, preserving any programmatic rules.
     */
    public static synchronized void reloadFromConfig(List<StructureAnchorRule> configRules) {
        CONFIG_RULES.clear();
        if (configRules != null) {
            CONFIG_RULES.addAll(configRules);
        }
        rebuildAll();
    }

    private static void rebuildAll() {
        List<StructureAnchorRule> merged = new ArrayList<>(MOD_RULES.size() + CONFIG_RULES.size());
        merged.addAll(CONFIG_RULES);
        merged.addAll(MOD_RULES);
        merged.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
        ALL_RULES = Collections.unmodifiableList(merged);
    }

    /** Immutable view of the rules, ordered high‑priority → low‑priority. */
    public static List<StructureAnchorRule> getAll() {
        return ALL_RULES;
    }

    /** Clears all registered rules (useful for test resets). */
    public static synchronized void clear() {
        MOD_RULES.clear();
        CONFIG_RULES.clear();
        ALL_RULES = List.of();
    }
}
