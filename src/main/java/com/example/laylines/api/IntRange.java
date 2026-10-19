package com.example.laylines.api;

/** Simple inclusive integer range used for capacity configuration. */
public record IntRange(int min, int max) {
    public IntRange {
        if (min > max) {
            throw new IllegalArgumentException("IntRange min must be <= max");
        }
    }

    /** Returns a random value within the range using the supplied RNG. */
    public int random(java.util.Random rng) {
        return rng.nextInt((max - min) + 1) + min;
    }

    /** Parses a range formatted as "min-max" or a single number "val". */
    public static IntRange fromString(String rangeStr) {
        String[] parts = rangeStr.split("-");
        if (parts.length == 2) {
            int min = Integer.parseInt(parts[0].trim());
            int max = Integer.parseInt(parts[1].trim());
            return new IntRange(min, max);
        } else if (parts.length == 1) {
            int val = Integer.parseInt(parts[0].trim());
            return new IntRange(val, val);
        }
        throw new IllegalArgumentException("Invalid IntRange format: " + rangeStr);
    }
}

