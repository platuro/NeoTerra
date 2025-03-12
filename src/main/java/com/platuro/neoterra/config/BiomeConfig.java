package com.platuro.neoterra.config;

import net.minecraftforge.common.config.Configuration;
import java.io.File;

public class BiomeConfig {
    private static final String CATEGORY_WORLD = "world_generation";
    private static final String CATEGORY_CLIMATE = "climate_settings";

    public static int MAX_WORLD_HEIGHT;
    public static int MAX_WORLD_WIDTH;
    public static float FROZEN_START;
    public static float COLD_START;
    public static float WARM_START;
    public static float CLIMATE_FADE;
    public static float WORLD_SHIFT_VALUE;
    public static float CONTINENT_SCALE_MULTIPLIER;

    public static final int POLAR_WAVES = 100;

    public static void loadConfig(File configFile) {
        Configuration config = new Configuration(configFile);
        config.load();

        MAX_WORLD_HEIGHT = config.getInt("maxWorldHeight", CATEGORY_WORLD, 4000, 256, 40000, "Maximum world height.");
        MAX_WORLD_WIDTH = config.getInt("maxWorldWidth", CATEGORY_WORLD, 10000, 512, 100000, "Maximum world width.");
        CONTINENT_SCALE_MULTIPLIER = config.getFloat("continentScaleMultiplier", CATEGORY_WORLD, 1, 0.1f, 10, "Continent scale multiplier.");
        WORLD_SHIFT_VALUE = (float) config.getFloat("worldShiftValue", CATEGORY_WORLD, 0.0f, -1f, 1f, "World shift value.");

        FROZEN_START = (float) config.getFloat("frozenStart", CATEGORY_CLIMATE, 0.95f, 0f, 1f, "Latitude where frozen biomes start (~75°-90°).");
        COLD_START = (float) config.getFloat("coldStart", CATEGORY_CLIMATE, 0.75f, 0f, 1f, "Latitude where cold biomes start (~50°-75°).");
        WARM_START = (float) config.getFloat("warmStart", CATEGORY_CLIMATE, 0.30f, 0f, 1f, "Latitude where warm biomes start (~20°-50°).");
        CLIMATE_FADE = (float) config.getFloat("climateFade", CATEGORY_CLIMATE, 0.08f, 0f, 1f, "Smooth transition between biome zones.");

        if (config.hasChanged()) {
            config.save();
        }
    }
}
