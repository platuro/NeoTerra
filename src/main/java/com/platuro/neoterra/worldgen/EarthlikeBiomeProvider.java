package com.platuro.neoterra.worldgen;

import net.minecraft.init.Biomes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.gen.NoiseGeneratorSimplex;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * A BiomeProvider that:
 *  - Uses fractal for ocean vs land
 *  - Wave-based lat transitions (FROZEN, COLD, WARM, HOT)
 *  - Wavy polar boundary
 *  - No rivers
 *  - Optionally adds Biomes O' Plenty biomes if BOP is installed (by reflection).
 */
public class EarthlikeBiomeProvider extends BiomeProvider {

    // ~~~~~~~~~ World & Boundaries ~~~~~~~~~
    private static final int MAX_PLANET_WIDTH = 10000;
    private static final int X_FADE_BAND      = 3000;

    private static final int POLAR_Z_LIMIT    = 4000;
    private static final int POLAR_FADE_BAND  = 800;

    // ~~~~~~~~~ Fractal Noise for Ocean ~~~~~~~~~
    private static final int    CONT_OCTAVES  = 5;
    private static final double CONT_PERSIST  = 0.5;
    private static final double CONT_SCALE    = 0.0002;
    private static final double CONT_LACUNAR  = 2.0;

    private static final int    DETAIL_OCTAVES   = 2;
    private static final double DETAIL_PERSIST   = 0.5;
    private static final double DETAIL_SCALE     = 0.01;
    private static final double DETAIL_LACUNAR   = 2.0;
    private static final double DETAIL_AMPLITUDE = 0.01;

    private static final double SHIFT_VALUE = -0.27;

    // ~~~~~~~~~ Ocean & Beach thresholds ~~~~~~~~~
    private static final double DEEP_OCEAN_LEVEL = -0.4;
    private static final double OCEAN_LEVEL      = -0.10;
    private static final double BEACH_LEVEL      = -0.07;
    private static final double SECOND_BEACH_LEVEL      = -0.1;

    // ~~~~~~~~~ Lat effect & Climate Zones ~~~~~~~~~
    private static final float  POLE_LIMIT   = 3000f;

    /**
     * latVal in [0..1].
     *   latVal>0.75 => Frozen
     *   latVal>0.50 => Cold
     *   latVal>0.25 => Warm
     *   else => Hot
     */
    private static final float FROZEN_START = 0.99f;  // ~75°-90° latitude (Polar regions)
    private static final float COLD_START   = 0.75f;  // ~50°-75° latitude (Cold temperate)
    private static final float WARM_START   = 0.30f;  // ~20°-50° latitude (Warm temperate & subtropical)
    private static final float CLIMATE_FADE = 0.08f;  // Smooth biome transitions

    // ~~~~~~~~~ Vanilla Biome Arrays ~~~~~~~~~
    private static final List<Biome> FROZEN_BASE = new ArrayList<>(Arrays.asList(
            Biomes.ICE_PLAINS,
            Biomes.ICE_MOUNTAINS
    ));
    private static final List<Biome> COLD_BASE = new ArrayList<>(Arrays.asList(
            Biomes.COLD_TAIGA,
            Biomes.COLD_TAIGA_HILLS
    ));
    private static final List<Biome> WARM_BASE = new ArrayList<>(Arrays.asList(
            Biomes.FOREST,
            Biomes.PLAINS,
            Biomes.SWAMPLAND
    ));
    private static final List<Biome> HOT_BASE = new ArrayList<>(Arrays.asList(
            Biomes.DESERT,
            Biomes.SAVANNA,
            Biomes.JUNGLE,
            Biomes.MESA
    ));

    // After reflection, we store final arrays
    private static Biome[] FROZEN_BIOMES;
    private static Biome[] COLD_BIOMES;
    private static Biome[] WARM_BIOMES;
    private static Biome[] HOT_BIOMES;
    private static Biome BOP_VOLCANO;

    // ~~~~~~~~~ Sub-biome lumps ~~~~~~~~~
    private static final double BIOME_PATCH_SCALE   = 0.001;
    private static final int    BIOME_PATCH_OCTAVES = 4;
    private static final double BIOME_PATCH_PERSIST = 0.5;
    private static final double BIOME_PATCH_LACUNAR = 2.0;

    // ~~~~~~~~~ Wave for lat & polar ~~~~~~~~~
    private static final double WAVE_SCALE     = 0.0006;
    private static final double WAVE_AMPLITUDE = 0.02;

    private static final double POLAR_WAVE_SCALE     = 0.001;
    private static final double POLAR_WAVE_AMPLITUDE = 100.0;

    // ~~~~~~~~~ NOISE INSTANCES ~~~~~~~~~
    private final NoiseGeneratorSimplex continentNoise;
    private final NoiseGeneratorSimplex detailNoise;
    private final NoiseGeneratorSimplex subBiomeNoise;
    private final NoiseGeneratorSimplex waveNoise;
    private final NoiseGeneratorSimplex polarWaveNoise;

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    //                 BOP REFLECTION
    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    static {
        // Start with the base vanilla sets
        List<Biome> frozenVanilla = new ArrayList<>(FROZEN_BASE);
        List<Biome> coldVanilla   = new ArrayList<>(COLD_BASE);
        List<Biome> warmVanilla   = new ArrayList<>(WARM_BASE);
        List<Biome> hotVanilla    = new ArrayList<>(HOT_BASE);

        // Attempt to load BOP
        try {
            // 1) Confirm BOP is installed
            Class<?> bopClass = Class.forName("biomesoplenty.api.biome.BOPBiomes");

            // 2) For each BOP field, we do getBOPBiomeOptional(bopClass, "alps") etc.
            Biome alps    = getBOPBiomeOptional(bopClass, "alps");
            Biome tundra  = getBOPBiomeOptional(bopClass, "tundra");
            Biome orchard = getBOPBiomeOptional(bopClass, "orchard");
            Biome lushDesert = getBOPBiomeOptional(bopClass, "lush_desert");
            Biome tropRainforest = getBOPBiomeOptional(bopClass, "tropical_rainforest");
            Biome coniferous_forest = getBOPBiomeOptional(bopClass, "coniferous_forest");
            Biome snowy_coniferous_forest = getBOPBiomeOptional(bopClass, "snowy_coniferous_forest");
            Biome dead_forest = getBOPBiomeOptional(bopClass, "dead_forest");
            Biome grove  = getBOPBiomeOptional(bopClass, "grove");
            Biome ominous_woods = getBOPBiomeOptional(bopClass, "ominous_woods");
            Biome bamboo_forest = getBOPBiomeOptional(bopClass, "bamboo_forest");
            BOP_VOLCANO = getBOPBiomeOptional(bopClass, "volcanic_island");
            Biome cherry_blossom_grove = getBOPBiomeOptional(bopClass, "cherry_blossom_grove");

            // 3) If they are not null, add them to the relevant list
            if (alps != null) {
                frozenVanilla.add(alps);
            }
            if (tundra != null) {
                hotVanilla.add(tundra);
            }
            if (orchard != null) {
                warmVanilla.add(orchard);
            }
            if (lushDesert != null) {
                hotVanilla.add(lushDesert);
            }
            if (tropRainforest != null) {
                hotVanilla.add(tropRainforest);
            }
            if (coniferous_forest != null) {
                warmVanilla.add(coniferous_forest);
            }
            if (snowy_coniferous_forest != null) {
                coldVanilla.add(snowy_coniferous_forest);
            }
            if (dead_forest != null) {
                hotVanilla.add(dead_forest);
            }
            if (grove != null) {
                hotVanilla.add(grove);
            }
            if (ominous_woods != null) {
                coldVanilla.add(ominous_woods);
            }
            if (bamboo_forest != null) {
                warmVanilla.add(bamboo_forest);
            }
            if(cherry_blossom_grove != null) {
                warmVanilla.add(cherry_blossom_grove);
            }

            System.out.println("Biomes O' Plenty detected! Added some BOP biome Optionals to arrays.");
        } catch (ClassNotFoundException e) {
            System.out.println("BOP not installed or not found. Using vanilla only.");
        } catch (Throwable t) {
            System.out.println("BOP reflection error. Using vanilla only.");
            t.printStackTrace();
        }

        // Convert lists to arrays
        FROZEN_BIOMES = frozenVanilla.toArray(new Biome[0]);
        COLD_BIOMES   = coldVanilla.toArray(new Biome[0]);
        WARM_BIOMES   = warmVanilla.toArray(new Biome[0]);
        HOT_BIOMES    = hotVanilla.toArray(new Biome[0]);
    }

    /** Helper: tries to fetch a public static Biome field from BOPBiomes by name. */
    private static Biome getBOPBiomeOptional(Class<?> bopClass, String fieldName) {
        try {
            Field f = bopClass.getField(fieldName);
            Object obj = f.get(null); // static field e.g. BOPBiomes.alps
            // The field is an Optional<Biome>
            if (obj instanceof com.google.common.base.Optional) {
                com.google.common.base.Optional<?> optionalBiome = (com.google.common.base.Optional<?>)obj;
                if (optionalBiome.isPresent() && optionalBiome.get() instanceof Biome) {
                    return (Biome)optionalBiome.get();
                }
            }
        } catch (NoSuchFieldException e) {
            System.out.println("No such BOP field: " + fieldName);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // ~~~~~~~~~ Constructor ~~~~~~~~~
    public EarthlikeBiomeProvider(long seed) {
        super();
        Random randContinent = new Random(seed);
        Random randDetail    = new Random(seed + 1);
        Random randSubBiome  = new Random(seed + 2);
        Random randWave      = new Random(seed + 3);
        Random randPolarWave = new Random(seed + 4);

        this.continentNoise = new NoiseGeneratorSimplex(randContinent);
        this.detailNoise    = new NoiseGeneratorSimplex(randDetail);
        this.subBiomeNoise  = new NoiseGeneratorSimplex(randSubBiome);
        this.waveNoise      = new NoiseGeneratorSimplex(randWave);
        this.polarWaveNoise = new NoiseGeneratorSimplex(randPolarWave);
    }

    public EarthlikeBiomeProvider() {
        this(12345L);
    }

    // ~~~~~~~~~ Overridden Methods ~~~~~~~~~
    @Override
    public Biome getBiome(BlockPos pos) {
        return pickBiome(pos.getX(), pos.getZ());
    }

    @Override
    public BlockPos findBiomePosition(int x, int z, int range, List<Biome> allowedBiomes, Random random) {
        System.out.println("Searching for biome in allowed list: " + allowedBiomes);
        for (int radius = 0; radius <= range; radius++) {
            // Search in a spiral pattern around (x, z) for a valid biome
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue; // Check perimeter only
                    BlockPos pos = new BlockPos(x + dx, 64, z + dz);
                    Biome biomeHere = pickBiome(pos.getX(), pos.getZ());
                    if (allowedBiomes.contains(biomeHere)) {
                        System.out.println("Found valid biome at " + pos + ": " + biomeHere.getRegistryName());
                        return pos;
                    }
                }
            }
        }
        System.out.println("ERROR: No valid biome found in range " + range + " around (" + x + "," + z + ")");
        return null;
    }



    @Override
    public Biome[] getBiomesForGeneration(Biome[] biomes, int x, int z, int width, int height) {
        if (biomes == null || biomes.length < width * height) {
            biomes = new Biome[width * height];
        }
        for (int i = 0; i < width * height; i++) {
            int localX = (x + (i % width)) << 2;
            int localZ = (z + (i / width)) << 2;
            biomes[i] = pickBiome(localX, localZ);
        }
        return biomes;
    }

    @Override
    public Biome[] getBiomes(@Nullable Biome[] listToReuse, int x, int z, int width, int depth, boolean cacheFlag) {
        if (listToReuse == null || listToReuse.length < width * depth) {
            listToReuse = new Biome[width * depth];
        }
        for (int i = 0; i < width * depth; i++) {
            int localX = x + (i % width);
            int localZ = z + (i / width);
            listToReuse[i] = pickBiome(localX, localZ);
        }
        return listToReuse;
    }

    @Override
    public List<Biome> getBiomesToSpawnIn() {
        List<Biome> result = new ArrayList<>();
        result.addAll(Arrays.asList(FROZEN_BIOMES));
        result.addAll(Arrays.asList(COLD_BIOMES));
        result.addAll(Arrays.asList(WARM_BIOMES));
        result.addAll(Arrays.asList(HOT_BIOMES));
        // Explicitly add vanilla stronghold biomes:
        result.add(Biomes.PLAINS);
        result.add(Biomes.DESERT);
        result.add(Biomes.EXTREME_HILLS);
        result.add(Biomes.FOREST);
        result.add(Biomes.SAVANNA);
        result.add(Biomes.TAIGA);
        // Also add beaches and oceans if you like:
        result.addAll(Arrays.asList(Biomes.BEACH, Biomes.OCEAN, Biomes.DEEP_OCEAN));
        return result;
    }


    @Override
    public boolean areBiomesViable(int centerX, int centerZ, int radius, List<Biome> allowed) {
        int step = 4;
        for (int xx = centerX - radius; xx <= centerX + radius; xx += step) {
            for (int zz = centerZ - radius; zz <= centerZ + radius; zz += step) {
                Biome b = pickBiome(xx, zz);
                if (!allowed.contains(b)) {
                    return false;
                }
            }
        }
        return true;
    }

    // ~~~~~~~~~ MAIN BIOME SELECTION ~~~~~~~~~
    private Biome pickBiome(int x, int z) {
        int absX = Math.abs(x);
        int absZ = Math.abs(z);

        // 1) fractal for ocean vs land
        double contVal = fractalNoise(continentNoise, x, z,
                CONT_OCTAVES, CONT_PERSIST, CONT_SCALE, CONT_LACUNAR);
        double detVal  = fractalNoise(detailNoise, x, z,
                DETAIL_OCTAVES, DETAIL_PERSIST, DETAIL_SCALE, DETAIL_LACUNAR)
                * DETAIL_AMPLITUDE;
        double finalVal = contVal + detVal + SHIFT_VALUE;

        // 2) fade near X boundary => ocean
        int distFromXEdge = MAX_PLANET_WIDTH - absX;
        if (distFromXEdge < 0) {
            return Biomes.DEEP_OCEAN;
        } else if (distFromXEdge < X_FADE_BAND) {
            finalVal = fadeTo(finalVal, -0.8, distFromXEdge, X_FADE_BAND);
        }

        // 3) wavy polar boundary
        double polarWave   = polarWaveNoise.getValue(x * POLAR_WAVE_SCALE, z * POLAR_WAVE_SCALE)
                * POLAR_WAVE_AMPLITUDE;
        double dynamicPole = POLAR_Z_LIMIT + polarWave;
        double distFromPole = dynamicPole - absZ;
        if (distFromPole < 0) {
            return Biomes.FROZEN_OCEAN;
        } else if (distFromPole < POLAR_FADE_BAND) {
            int distInt = (int)distFromPole;
            if (distInt < 0) distInt = 0;
            finalVal = fadeTo(finalVal, -0.8, distInt, POLAR_FADE_BAND);
        }

        // latitude calculation
        float baseLatVal = (float) absZ / POLE_LIMIT;

        // Determine climate
        boolean isColdClimate = baseLatVal > COLD_START;

        // 4) ocean thresholds with cold climate check
        if (finalVal < DEEP_OCEAN_LEVEL) {
            return pickSubBiome(Biomes.DEEP_OCEAN, x, z);
        } else if (finalVal < OCEAN_LEVEL) {
            return pickSubBiome(Biomes.OCEAN, x, z);
        }

        if(isColdClimate) {
            if(finalVal < BEACH_LEVEL) {
                return Biomes.COLD_BEACH;
            }
        }

        // 6) land => pick climate zone
        return pickLatitudeBiome(x, z, baseLatVal);
    }

    // ~~~~~~~~~ LATITUDE-BASED CLIMATE with wave ~~~~~~~~~
    private Biome pickLatitudeBiome(int x, int z, float baseLatVal) {
        // wave for lat boundary
        double wv = waveNoise.getValue(x * WAVE_SCALE, z * WAVE_SCALE) * WAVE_AMPLITUDE;
        float latVal = (float)(baseLatVal + wv);
        if (latVal < 0f) latVal = 0f;
        if (latVal > 1f) latVal = 1f;

        if (latVal > FROZEN_START - CLIMATE_FADE) {
            float alpha = fadeAlpha(latVal, FROZEN_START - CLIMATE_FADE, FROZEN_START + CLIMATE_FADE);
            if (latVal < FROZEN_START) {
                return blendTwoBiomes(COLD_BIOMES, FROZEN_BIOMES, alpha, x, z);
            }
            return pickSubBiome(FROZEN_BIOMES, x, z);
        }

        if (latVal > COLD_START - CLIMATE_FADE) {
            float alpha = fadeAlpha(latVal, COLD_START - CLIMATE_FADE, COLD_START + CLIMATE_FADE);
            if (latVal < COLD_START) {
                return blendTwoBiomes(WARM_BIOMES, COLD_BIOMES, alpha, x, z);
            }
            return pickSubBiome(COLD_BIOMES, x, z);
        }

        if (latVal > WARM_START - CLIMATE_FADE) {
            float alpha = fadeAlpha(latVal, WARM_START - CLIMATE_FADE, WARM_START + CLIMATE_FADE);
            if (latVal < WARM_START) {
                return blendTwoBiomes(HOT_BIOMES, WARM_BIOMES, alpha, x, z);
            }
            return pickSubBiome(WARM_BIOMES, x, z);
        }

        // near equator => HOT
        return pickSubBiome(HOT_BIOMES, x, z);
    }

    // ~~~~~~~~~ Sub-biome lumps with ocean-restricted rare biomes ~~~~~~~~~
    private Biome pickSubBiome(Biome[] arr, int x, int z) {
        double val = fractalNoise(subBiomeNoise, x, z,
                BIOME_PATCH_OCTAVES, BIOME_PATCH_PERSIST,
                BIOME_PATCH_SCALE, BIOME_PATCH_LACUNAR);
        double t = (val + 1.0) / 2.0;
        int idx = (int) (t * arr.length);
        if (idx >= arr.length) idx = arr.length - 1;
        return arr[idx];  // Default sub-biome
    }

    private Biome pickSubBiome(Biome baseBiome, int x, int z) {
        // 1️⃣ Check if this is a deep ocean biome
        boolean isDeepOcean = baseBiome == Biomes.DEEP_OCEAN;

        // 2️⃣ Calculate normalized latitude (0 at equator, 1 at pole)
        float baseLatVal = (float) Math.abs(z) / POLE_LIMIT;

        // 3️⃣ Generate noise values for rare biome selection
        double rareBiomeNoise = fractalNoise(subBiomeNoise, x, z, 3, 0.5, 0.0004, 2.0);
        double rareBiomeChance = (rareBiomeNoise + 1.0) / 2.0;  // Normalize to [0,1]
        double breakUpNoise = fractalNoise(subBiomeNoise, x, z, 4, 0.6, 0.002, 3.0);

        // 4️⃣ Additional wavy effect for smoother blending
        double waveEffect = fractalNoise(waveNoise, x, z, 2, 0.5, 0.0005, 3.0) * 0.1;
        baseLatVal = Math.min(1.0f, Math.max(0.0f, baseLatVal + (float) waveEffect)); // Apply wave shift

        // 5️⃣ Check if within safe world boundaries (No islands in fade-out regions!)
        double distanceFromEdge = MAX_PLANET_WIDTH - Math.abs(x);
        double edgeFadeFactor = Math.min(1.0, distanceFromEdge / X_FADE_BAND);
        if (edgeFadeFactor < 0.6) {
            return baseBiome;  // Too close to the edge, no island spawn!
        }

        if (isDeepOcean) {
            // 6️⃣ Build a list of all eligible rare biomes
            List<Biome> eligibleBiomes = new ArrayList<>();

            // Mushroom Island eligibility criteria
            if (baseLatVal < 0.9 && rareBiomeChance > 0.84 && breakUpNoise < 0.1) {
                eligibleBiomes.add(Biomes.MUSHROOM_ISLAND);
            }

            // Volcano eligibility criteria
            if (BOP_VOLCANO != null &&
                    baseLatVal > 0.2f && baseLatVal < 0.7f &&
                    rareBiomeChance > 0.7 && breakUpNoise < 0.3) {
                eligibleBiomes.add(BOP_VOLCANO);
            }

            if (!eligibleBiomes.isEmpty()) {
                // 7️⃣ Apply Island Spacing & Soft Boundaries
                final int ISLAND_SPACING = 1200;  // More spacing for less clustering
                final int ISLAND_RADIUS = 300;    // Slightly larger island radius

                int cellX = Math.floorDiv(x, ISLAND_SPACING);
                int cellZ = Math.floorDiv(z, ISLAND_SPACING);
                int centerX = cellX * ISLAND_SPACING + ISLAND_SPACING / 2;
                int centerZ = cellZ * ISLAND_SPACING + ISLAND_SPACING / 2;

                double dx = x - centerX;
                double dz = z - centerZ;
                double distance = Math.sqrt(dx * dx + dz * dz);

                // Use noise for extra wavy effect in island placement
                double islandWave = fractalNoise(subBiomeNoise, x, z, 2, 0.6, 0.0008, 2.5);
                boolean isWithinIslandRadius = (distance <= ISLAND_RADIUS + (islandWave * 50));

                // 🚫 Final Check: Don't generate islands outside of safe boundaries!
                if (isWithinIslandRadius && edgeFadeFactor > 0.6) {
                    double selectionNoise = fractalNoise(subBiomeNoise, cellX, cellZ, 3, 0.7, 0.001, 2.5);
                    int index = (int) (Math.abs(selectionNoise) * eligibleBiomes.size()) % eligibleBiomes.size();
                    return eligibleBiomes.get(index);
                }
            }
        }

        // 8️⃣ Default: return the base deep ocean biome
        return baseBiome;
    }


    private Biome blendTwoBiomes(Biome[] arrA, Biome[] arrB, float alpha, int x, int z) {
        return (alpha < 0.5f)
                ? pickSubBiome(arrA, x, z)
                : pickSubBiome(arrB, x, z);
    }

    // ~~~~~~~~~ NOISE & FADE HELPERS ~~~~~~~~~
    private double fractalNoise(NoiseGeneratorSimplex gen,
                                double x, double z,
                                int octaves,
                                double persistence,
                                double scale,
                                double lacunarity)
    {
        double sum = 0.0;
        double max = 0.0;
        double amp = 1.0;
        double freq = scale;

        for (int i = 0; i < octaves; i++) {
            double noiseVal = gen.getValue(x * freq, z * freq);
            sum += noiseVal * amp;
            max += amp;
            amp  *= persistence;
            freq *= lacunarity;
        }
        return sum / max; // ~[-1..+1]
    }

    private double fadeTo(double val, double targetVal, int dist, int fadeBand) {
        double alpha = 1.0 - ((double)dist / fadeBand);
        if (alpha < 0.0) alpha = 0.0;
        if (alpha > 1.0) alpha = 1.0;
        return (1.0 - alpha)*val + alpha*targetVal;
    }

    private float fadeAlpha(float latVal, float minVal, float maxVal) {
        if (latVal <= minVal) return 0f;
        if (latVal >= maxVal) return 1f;
        return (latVal - minVal) / (maxVal - minVal);
    }
}
