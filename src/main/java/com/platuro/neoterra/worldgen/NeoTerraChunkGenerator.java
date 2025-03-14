package com.platuro.neoterra.worldgen;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.gen.NoiseGeneratorPerlin;
import net.minecraftforge.common.MinecraftForge;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class NeoTerraChunkGenerator implements IChunkGenerator {

    private final World world;
    private final EarthlikeBiomeProvider biomeProvider;
    private final NoiseGeneratorPerlin terrainNoise;
    private final NoiseGeneratorPerlin riverNoise;
    private final Random random;
    private final int seaLevel = 63;

    public NeoTerraChunkGenerator(World world) {
        this.world = world;
        this.biomeProvider = new EarthlikeBiomeProvider(world.getSeed());
        this.random = new Random(world.getSeed());
        this.terrainNoise = new NoiseGeneratorPerlin(random, 3);
        this.riverNoise = new NoiseGeneratorPerlin(random, 2);
    }

    @Override
    public Chunk generateChunk(int chunkX, int chunkZ) {
        ChunkPrimer primer = new ChunkPrimer();
        double[][] heightMap = new double[16][16];

        // **Completely Remove Distance-Based Scaling**
        double fixedHeightFactor = 1.0;  // Ensure land and ocean behave consistently

        // First pass: Generate terrain height map
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = (chunkX << 4) + x;
                int worldZ = (chunkZ << 4) + z;

                // Get biome and neighboring biomes for smooth transitions
                Biome biome = biomeProvider.getBiome(new BlockPos(worldX, 0, worldZ));
                float[] transitionData = getBiomeTransitionData(worldX, worldZ);
                float blendFactor = transitionData[0]; // 0 = full ocean, 1 = full land
                float avgBaseHeight = transitionData[1];
                float avgHeightVariation = transitionData[2];

                // **Absolute terrain noise without scaling over distance**
                double baseNoise = terrainNoise.getValue(worldX * 0.002, worldZ * 0.002) * 10 * fixedHeightFactor;
                double terrainHeight = seaLevel + baseNoise + avgBaseHeight * 8 + avgHeightVariation * 4;

                // **Ensure Oceans Stay Below Sea Level**
                double oceanDepth = getEnforcedOceanDepth(worldX, worldZ, blendFactor);

                // **Fix land not rising aggressively over distance**
                double landBoost = blendFactor * 5;  // Ensures smooth elevation growth but no world scaling

                // **Smooth ocean-to-land transition while keeping heights stable**
                double transitionFactor = MathHelper.clamp((blendFactor - 0.4f) / 0.8f, 0, 1);
                terrainHeight = terrainHeight * transitionFactor + oceanDepth * (1 - transitionFactor) + landBoost;

                // Clamp height to prevent extreme terrain
                double finalHeight = MathHelper.clamp(terrainHeight, 1, 255);
                heightMap[x][z] = finalHeight;
            }
        }

        // Second pass: Apply height map to terrain
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int finalHeight = (int) heightMap[x][z];
                Biome biome = biomeProvider.getBiome(new BlockPos((chunkX << 4) + x, 0, (chunkZ << 4) + z));
                if (finalHeight < seaLevel && !isOceanBiome(biome)) {
                    biome = Biomes.RIVER;
                }
                generateTerrainColumn(primer, x, z, finalHeight, biome);
            }
        }

        // Assign biome data
        Chunk chunk = new Chunk(world, primer, chunkX, chunkZ);
        byte[] biomeArray = chunk.getBiomeArray();
        Biome[] biomes = biomeProvider.getBiomes(null, chunkX * 16, chunkZ * 16, 16, 16, true);

        for (int i = 0; i < biomeArray.length; i++) {
            biomeArray[i] = (byte) (Biome.getIdForBiome(biomes[i]) & 0xFF);
        }

        chunk.generateSkylightMap();
        return chunk;
    }

    private void generateTerrainColumn(ChunkPrimer primer, int x, int z, int height, Biome biome) {
        primer.setBlockState(x, 0, z, Blocks.BEDROCK.getDefaultState());

        IBlockState topBlock = biome.topBlock;
        IBlockState fillerBlock = biome.fillerBlock;

        // If the biome is below sea level and it's not an ocean biome, classify it as a river
        if (height < seaLevel && !isOceanBiome(biome)) {
            biome = Biomes.RIVER;  // Override to river biome
            topBlock = Biomes.RIVER.topBlock;
            fillerBlock = Biomes.RIVER.fillerBlock;
        }

        for (int y = 1; y <= height; y++) {
            if (y > height - 2) {  // Use top block at the surface
                primer.setBlockState(x, y, z, topBlock);
            } else {
                primer.setBlockState(x, y, z, fillerBlock);
            }
        }

        // Add water for rivers and oceans
        for (int y = height + 1; y <= seaLevel; y++) {
            primer.setBlockState(x, y, z, Blocks.WATER.getDefaultState());
        }
    }


    // **Ensures Oceans Stay Below Sea Level, But Keeps Terrain Flat**
    private double getEnforcedOceanDepth(int worldX, int worldZ, float blendFactor) {
        double oceanNoise = terrainNoise.getValue(worldX * 0.002, worldZ * 0.002) * 3;
        double oceanBase = seaLevel - 12 + oceanNoise; // Ensures deep oceans
        double shallows = seaLevel - 3; // Keeps shallows higher
        return MathHelper.clamp(oceanBase + blendFactor * (shallows - oceanBase), oceanBase, shallows);
    }

    // **Smooth biome transition calculation**
    private float[] getBiomeTransitionData(int worldX, int worldZ) {
        int sampleRadius = 2;
        int totalSamples = 0;
        int nonOceanCount = 0;
        float baseHeightSum = 0;
        float heightVariationSum = 0;

        for (int dx = -sampleRadius; dx <= sampleRadius; dx++) {
            for (int dz = -sampleRadius; dz <= sampleRadius; dz++) {
                Biome sampleBiome = biomeProvider.getBiome(new BlockPos(worldX + dx * 4, 0, worldZ + dz * 4));
                totalSamples++;

                if (sampleBiome != Biome.getBiome(0) && sampleBiome != Biome.getBiome(24)) { // Not an ocean
                    nonOceanCount++;
                }

                baseHeightSum += sampleBiome.getBaseHeight();
                heightVariationSum += sampleBiome.getHeightVariation();
            }
        }

        float blend = (float) nonOceanCount / totalSamples;
        float avgBaseHeight = baseHeightSum / totalSamples;
        float avgHeightVariation = heightVariationSum / totalSamples;

        return new float[]{blend, avgBaseHeight, avgHeightVariation};
    }

    // Helper method to check if a biome is an ocean
    private boolean isOceanBiome(Biome biome) {
        return biome == Biomes.OCEAN || biome == Biomes.DEEP_OCEAN || biome == Biomes.FROZEN_OCEAN;
    }

    @Override
    public void populate(int chunkX, int chunkZ) {
        BlockPos pos = new BlockPos(chunkX << 4, 0, chunkZ << 4);
        Biome biome = world.getBiome(pos);
        biome.decorate(world, random, pos);
    }

    @Override
    public boolean generateStructures(Chunk chunkIn, int x, int z) {
        return false;
    }

    @Override
    public List<Biome.SpawnListEntry> getPossibleCreatures(EnumCreatureType creatureType, BlockPos pos) {
        return Collections.emptyList();
    }

    @Nullable
    @Override
    public BlockPos getNearestStructurePos(World worldIn, String structureName, BlockPos position, boolean findUnexplored) {
        return null;
    }

    @Override
    public void recreateStructures(Chunk chunkIn, int x, int z) { }

    @Override
    public boolean isInsideStructure(World worldIn, String structureName, BlockPos pos) {
        return false;
    }
}