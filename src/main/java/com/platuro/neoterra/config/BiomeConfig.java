package com.platuro.neoterra.config;

public class BiomeConfig {
    public static final int MAX_WORLD_HEIGHT = 4000;
    public static final int MAX_WORLD_WIDTH = 10000;
    public static final float FROZEN_START = 0.99f;  // ~75°-90° latitude (Polar regions)
    public static final float COLD_START   = 0.75f;  // ~50°-75° latitude (Cold temperate)
    public static final float WARM_START   = 0.30f;  // ~20°-50° latitude (Warm temperate & subtropical)
    public static final float CLIMATE_FADE = 0.08f;  // Smooth biome transitions
    public static final float WORLD_SHIFT_VALUE = 0f;
}
