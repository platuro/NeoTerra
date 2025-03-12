package com.platuro.neoterra.worldgen;

import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.terraingen.OreGenEvent;
import net.minecraftforge.fml.common.IWorldGenerator;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.io.File;
import java.util.*;

public class NeoOreGenerator implements IWorldGenerator {

    private static final int MAX_PLANET_WIDTH = 10000;
    private final Map<String, OreVein> oreVeins = new HashMap<>();
    private final File configFile;

    public NeoOreGenerator(File configDir) {
        MinecraftForge.ORE_GEN_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(this);
        GameRegistry.registerWorldGenerator(this, 0);
        this.configFile = new File(configDir, "oregen.cfg");
    }

    private void loadConfig(File configFile) {
        Configuration config = new Configuration(configFile);
        config.load();

        String[] oreData = config.getStringList("Ores", "ores", detectOres(), "Define ore veins");

        FMLLog.info("[NeoOreGen] Loading Config File: %s", configFile.getAbsolutePath());
        FMLLog.info("[NeoOreGen] Found %d ores in config!", oreData.length);

        for (String entry : oreData) {

            String[] parts = entry.split(",");

            FMLLog.info("[NeoOreGen] Parsed Parts (%d): %s", parts.length, Arrays.toString(parts));

            if (parts.length != 10) {  // Expecting 9 parts
                FMLLog.info("[NeoOreGen] Invalid entry (wrong number of values): %s", entry);
                continue;
            }

            String oreName = parts[0].trim();
            String blockName = parts[1].trim();
            int veinSize = Integer.parseInt(parts[2].trim());
            int minY = Integer.parseInt(parts[3].trim());
            int maxY = Integer.parseInt(parts[4].trim());
            double spawnChance = Double.parseDouble(parts[5].trim());
            String biomeTypeString = parts[6].trim().toUpperCase();

            BiomeDictionary.Type biomeType = biomeTypeString.equals("ANY") ? null : BiomeDictionary.Type.getType(biomeTypeString);

            double minLatitude = Double.parseDouble(parts[7].trim());
            double maxLatitude = Double.parseDouble(parts[8].trim());
            double densityMultiplier = Double.parseDouble(parts[9].trim());  // New parameter

            oreVeins.put(oreName, new OreVein(oreName, blockName, veinSize, minY, maxY, spawnChance, biomeType, minLatitude, maxLatitude, densityMultiplier));
            FMLLog.info("[NeoOreGen] Registered Vein: %s - Block: %s - Biome: %s", oreName, blockName, biomeTypeString);
        }

        config.save();
    }


    private String[] detectOres() {
        List<String> detectedOres = new ArrayList<>();

        for (ResourceLocation resource : ForgeRegistries.BLOCKS.getKeys()) {
            Block block = ForgeRegistries.BLOCKS.getValue(resource);
            String blockName = resource.getResourcePath();

            if (block != null && blockName.contains("ore") && !blockName.equals("lit_redstone_ore")) {
                String modId = resource.getResourceDomain();
                detectedOres.add(String.format("%s,%s:%s,30,5,60,0.5,ANY,0.0,1.0,2.0", blockName, modId, blockName));
            }
        }

        return detectedOres.toArray(new String[0]);
    }


    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world, IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        if (world.provider.getDimension() != 0) return;

        if (chunkX % 4 == 0 && chunkZ % 4 == 0) {
            generateOreVeins(world, random, chunkX, chunkZ);
        }
    }

    private void generateOreVeins(World world, Random random, int chunkX, int chunkZ) {
        Biome biome = world.getBiome(new BlockPos(chunkX * 16, 64, chunkZ * 16));
        Set<BiomeDictionary.Type> biomeTypes = BiomeDictionary.getTypes(biome);
        double latitude = getLatitude(chunkZ * 16);
        List<OreVein> possibleVeins = new ArrayList<>();

        for (OreVein vein : oreVeins.values()) {
            if (vein.isValidBiome(biomeTypes) && vein.isValidLatitude(latitude)) {
                possibleVeins.add(vein);
            }
        }

        if (!possibleVeins.isEmpty()) {
            OreVein selectedVein = possibleVeins.get(random.nextInt(possibleVeins.size()));
            generateOreCluster(world, random, chunkX, chunkZ, selectedVein);
            FMLLog.info("[NeoOreGen] Selected %s for Chunk [%d, %d]", selectedVein.oreName, chunkX, chunkZ);
        }
    }


    private void generateOreCluster(World world, Random random, int chunkX, int chunkZ, OreVein vein) {
        int minY = vein.minY;
        int maxY = vein.maxY;

        int centerX = chunkX * 16 + 8;
        int centerZ = chunkZ * 16 + 8;
        int centerY = minY + random.nextInt(maxY - minY);

        int veinLength = Math.max(12, vein.veinSize);
        int veinWidth = Math.max(6, vein.veinSize);
        int veinHeight = Math.max(4, vein.veinSize);

        FMLLog.info("[NeoOreGen] Creating Vein %s at [%d, %d, %d] - Density: %.2f",
                vein.oreName, centerX, centerY, centerZ, vein.densityMultiplier);

        for (int i = 0; i < veinLength; i++) {
            for (int j = 0; j < veinWidth; j++) {
                for (int k = 0; k < veinHeight; k++) {
                    int x = centerX + (random.nextInt(veinWidth) - veinWidth / 2);
                    int y = Math.max(minY, Math.min(centerY + (random.nextInt(veinHeight) - veinHeight / 2), maxY));
                    int z = centerZ + (random.nextInt(veinWidth) - veinWidth / 2);

                    // 🔥 **Directly apply density multiplier to force full placement**
                    double placementChance = Math.min(1.0, vein.densityMultiplier / 10.0);  // Allows up to 100% fill

                    if (random.nextDouble() < placementChance) {
                        world.setBlockState(new BlockPos(x, y, z), getOreBlock(vein.blockName), 2);
                        FMLLog.info("[NeoOreGen] Placed %s at [%d, %d, %d]", vein.oreName, x, y, z);
                    }
                }
            }
        }
    }


    private net.minecraft.block.state.IBlockState getOreBlock(String blockName) {
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blockName));
        return block != null ? block.getDefaultState() : net.minecraft.init.Blocks.STONE.getDefaultState();
    }

    @SubscribeEvent
    public void onOreGen(OreGenEvent.GenerateMinable event) {
        event.setResult(OreGenEvent.Result.DENY);
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        loadConfig(this.configFile);
    }

    private double getLatitude(int z) {
        double absZ = Math.abs(z);
        return Math.min(absZ / (MAX_PLANET_WIDTH / 2.0), 1.0);
    }

    static class OreVein {
        String oreName;
        String blockName;
        int veinSize;
        int minY;
        int maxY;
        double spawnChance;
        BiomeDictionary.Type requiredBiome; // Null = Any Biome
        double minLatitude;
        double maxLatitude;
        double densityMultiplier;  // NEW

        OreVein(String oreName, String blockName, int veinSize, int minY, int maxY, double spawnChance, BiomeDictionary.Type requiredBiome, double minLatitude, double maxLatitude, double densityMultiplier) {
            this.oreName = oreName;
            this.blockName = blockName;
            this.veinSize = veinSize;
            this.minY = minY;
            this.maxY = maxY;
            this.spawnChance = spawnChance;
            this.requiredBiome = requiredBiome;
            this.minLatitude = minLatitude;
            this.maxLatitude = maxLatitude;
            this.densityMultiplier = densityMultiplier; // NEW
        }

        boolean isValidBiome(Set<BiomeDictionary.Type> biomeTypes) {
            return requiredBiome == null || biomeTypes.contains(requiredBiome);
        }

        boolean isValidLatitude(double latitude) {
            return latitude >= minLatitude && latitude <= maxLatitude;
        }
    }
}
