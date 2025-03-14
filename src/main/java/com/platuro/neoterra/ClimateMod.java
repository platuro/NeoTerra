package com.platuro.neoterra;

import com.platuro.neoterra.config.BiomeConfig;
import com.platuro.neoterra.handlers.PlayerEventHandler;
import com.platuro.neoterra.worldgen.EarthlikeBiomeProvider;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.*;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.*;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.lang.reflect.Field;

@Mod(modid = ClimateMod.MODID, name = ClimateMod.NAME, version = ClimateMod.VERSION)
@Mod.EventBusSubscriber(modid = ClimateMod.MODID)
public class ClimateMod {
    public static final String MODID = "neoterra";
    public static final String NAME = "NeoTerra";
    public static final String VERSION = "0.1";

    private static Logger logger = LogManager.getLogger(NAME);
    private static File configFileBiome;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        File configDir = event.getModConfigurationDirectory();
        configFileBiome = new File(configDir, "neoterra/biome_config.cfg");
        BiomeConfig.loadConfig(configFileBiome);
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new PlayerEventHandler());
        MinecraftForge.EVENT_BUS.register(this);
    }

    private static Field getBiomeProviderField() throws NoSuchFieldException {
        for (String fieldName : new String[]{"biomeProvider", "field_76578_c"}) {
            try {
                Field f = WorldProvider.class.getDeclaredField(fieldName);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException("Could not find biomeProvider field");
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCreateSpawn(WorldEvent.CreateSpawnPosition event) {
        try {
            World world = event.getWorld();
            if (world == null || world.isRemote) return;

            Field biomeProviderField = getBiomeProviderField();
            BiomeProvider currentProvider = (BiomeProvider) biomeProviderField.get(world.provider);

            if (!(currentProvider instanceof EarthlikeBiomeProvider)) {
                biomeProviderField.set(world.provider, new EarthlikeBiomeProvider(world.getSeed()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SubscribeEvent
    public static void onWorldLoad(WorldEvent.Load event) {
        BiomeConfig.loadConfig(configFileBiome);
        World world = event.getWorld();

        if (!world.isRemote) {
            try {
                Field biomeProviderField = getBiomeProviderField();
                BiomeProvider currentProvider = (BiomeProvider) biomeProviderField.get(world.provider);

                if (!(currentProvider instanceof EarthlikeBiomeProvider)) {
                    biomeProviderField.set(world.provider, new EarthlikeBiomeProvider(world.getSeed()));
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
