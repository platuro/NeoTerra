package com.platuro.neoterra.config;

import com.platuro.neoterra.helpers.BOP;
import net.minecraft.init.Biomes;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.config.Configuration;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

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

    public static final int POLAR_WAVES = 10;

    private static final Map<Biome, Double> BIOME_WEIGHTS = new HashMap<>();

    // Postion of Biomes
    public static Biome[] FROZEN_COASTAL_BIOMES = { Biomes.ICE_PLAINS };
    public static Biome[] FROZEN_CENTRAL_BIOMES = { Biomes.ICE_MOUNTAINS };

    public static Biome[] COLD_COASTAL_BIOMES = { };
    public static Biome[] COLD_CENTRAL_BIOMES = { Biomes.COLD_TAIGA_HILLS };

    public static Biome[] WARM_COASTAL_BIOMES = { Biomes.BEACH, Biomes.SWAMPLAND };
    public static Biome[] WARM_CENTRAL_BIOMES = { Biomes.FOREST, Biomes.PLAINS };

    public static Biome[] HOT_COASTAL_BIOMES = { Biomes.DESERT } ;
    public static Biome[] HOT_CENTRAL_BIOMES = { Biomes.SAVANNA, Biomes.JUNGLE, Biomes.MESA };

    public static void loadConfig(File configFile) {
        Configuration config = new Configuration(configFile);
        config.load();

        MAX_WORLD_HEIGHT = config.getInt("maxWorldHeight", CATEGORY_WORLD, 10000, 512, 100000, "Maximum world height.");
        MAX_WORLD_WIDTH = config.getInt("maxWorldWidth", CATEGORY_WORLD, 10000, 512, 100000, "Maximum world width.");
        CONTINENT_SCALE_MULTIPLIER = config.getFloat("continentScaleMultiplier", CATEGORY_WORLD, 1, 0.1f, 10, "Continent scale multiplier.");
        WORLD_SHIFT_VALUE = (float) config.getFloat("worldShiftValue", CATEGORY_WORLD, 0.0f, -1f, 1f, "World shift value.");

        FROZEN_START = (float) config.getFloat("frozenStart", CATEGORY_CLIMATE, 0.95f, 0f, 1f, "Latitude where frozen biomes start (~75°-90°).");
        COLD_START = (float) config.getFloat("coldStart", CATEGORY_CLIMATE, 0.75f, 0f, 1f, "Latitude where cold biomes start (~50°-75°).");
        WARM_START = (float) config.getFloat("warmStart", CATEGORY_CLIMATE, 0.30f, 0f, 1f, "Latitude where warm biomes start (~20°-50°).");
        CLIMATE_FADE = (float) config.getFloat("climateFade", CATEGORY_CLIMATE, 0.08f, 0f, 1f, "Smooth transition between biome zones.");

        setBiomeWeight(Biomes.SWAMPLAND, 0.2f);
        setBiomeWeight(BOP.getBOPBiome("bamboo_forest"), 0.2f);
        setBiomeWeight(BOP.getBOPBiome("lush_desert"), 0.1f);
        setBiomeWeight(BOP.getBOPBiome("tundra"), 0.2f);
        setBiomeWeight(BOP.getBOPBiome("orchard"), 0.4f);

        if (config.hasChanged()) {
            config.save();
        }
    }

    public static void setBiomeWeight(Biome biome, double weight) {
        if(biome != null) {
            BIOME_WEIGHTS.put(biome, weight);
        }
    }

    public static double getBiomeWeight(Biome biome) {
        return BIOME_WEIGHTS.getOrDefault(biome, 1.0); // Default weight 1.0
    }
}
