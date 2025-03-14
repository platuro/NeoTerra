package com.platuro.neoterra.worldgen;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.gen.NoiseGeneratorPerlin;

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
    private final int minLandHeight = seaLevel + 1;  // Ensure land is always slightly above sea level

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

        // First pass: Generate terrain height map
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = (chunkX << 4) + x;
                int worldZ = (chunkZ << 4) + z;

                // Get biome and smooth transition values
                Biome biome = biomeProvider.getBiome(new BlockPos(worldX, 0, worldZ));
                float[] transitionData = getBiomeTransitionData(worldX, worldZ);
                float blendFactor = transitionData[0]; // 0 = full ocean, 1 = full land
                float avgBaseHeight = transitionData[1];
                float avgHeightVariation = transitionData[2];

                // **Generate smoother terrain**
                double baseNoise = terrainNoise.getValue(worldX * 0.002, worldZ * 0.002) * 5; // Reduced impact
                double terrainHeight = seaLevel + baseNoise + avgBaseHeight * 4 + avgHeightVariation * 2;

                // **Ensure Only Ocean Biomes Stay Below Sea Level**
                double oceanDepth = getEnforcedOceanDepth(worldX, worldZ, blendFactor, biome);

                // **Force Land Biomes to Have a Minimum Height**
                if (!isOceanBiome(biome)) {
                    terrainHeight = Math.max(terrainHeight, minLandHeight); // Ensures land stays above water
                }

                // **Introduce a gradual coastal buffer** for smoother land transitions
                double landBoost = MathHelper.clamp(blendFactor * 4, 0, 6);
                if (blendFactor > 0.1 && blendFactor < 0.6) { // Apply near the coast
                    double coastFactor = (blendFactor - 0.1) / (0.6 - 0.1);
                    landBoost *= coastFactor; // Gradual elevation rise
                    terrainHeight = Math.max(terrainHeight, seaLevel + 1); // Ensure coastline is flat before rising
                }

                // **Apply gradual land elevation instead of instant cliffs**
                double transitionFactor = MathHelper.clamp((blendFactor - 0.05f) / 0.5f, 0, 1);
                terrainHeight = terrainHeight * transitionFactor + oceanDepth * (1 - transitionFactor) + landBoost;

                // **Clamp height to prevent extreme elevation jumps**
                double finalHeight = MathHelper.clamp(terrainHeight, 1, 255);
                heightMap[x][z] = finalHeight;
            }
        }

        // Second pass: Apply height map to terrain
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int finalHeight = (int) heightMap[x][z];
                Biome biome = biomeProvider.getBiome(new BlockPos((chunkX << 4) + x, 0, (chunkZ << 4) + z));
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

        for (int y = 1; y <= height; y++) {
            if (y > height - 2) {  // Use top block at the surface
                primer.setBlockState(x, y, z, topBlock);
            } else {
                primer.setBlockState(x, y, z, fillerBlock);
            }
        }

        // Add water for oceans only
        if (isOceanBiome(biome)) {
            for (int y = height + 1; y <= seaLevel; y++) {
                primer.setBlockState(x, y, z, Blocks.WATER.getDefaultState());
            }
        }
    }

    private boolean isOceanBiome(Biome biome) {
        return biome == Biome.getBiome(0) || biome == Biome.getBiome(24) || biome == Biome.getBiome(10);
    }

    private double getEnforcedOceanDepth(int worldX, int worldZ, float blendFactor, Biome biome) {
        if (isOceanBiome(biome)) {
            double oceanNoise = terrainNoise.getValue(worldX * 0.002, worldZ * 0.002) * 1.0; // Reduced noise
            double oceanBase = seaLevel - 8 + oceanNoise;  // Reduce depth slightly
            double shallows = seaLevel - 3; // Higher shallows for better beaches
            return MathHelper.clamp(oceanBase + (blendFactor * (shallows - oceanBase)), oceanBase, shallows);
        }
        return seaLevel; // Ensure land stays above water
    }

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

                if (!isOceanBiome(sampleBiome)) {
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
