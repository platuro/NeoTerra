package com.platuro.neoterra;

import com.platuro.neoterra.config.BiomeConfig;
import com.platuro.neoterra.handlers.PlayerEventHandler;
import com.platuro.neoterra.worldgen.EarthlikeBiomeProvider;
import com.platuro.neoterra.worldgen.NeoOreGenerator;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldProviderSurface;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.terraingen.OreGenEvent;
import net.minecraftforge.event.terraingen.PopulateChunkEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
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
        logger.info("NeoClimate PreInit completed.");
        File configDir = event.getModConfigurationDirectory();
        configFileBiome = new File(configDir, "neoterra/biome_config.cfg");
        BiomeConfig.loadConfig(configFileBiome);
        //new NeoOreGenerator(configDir);
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new PlayerEventHandler());
    }

    private static Field getBiomeProviderField() throws NoSuchFieldException {
        // In a dev environment, the field might be called "biomeProvider"
        // In an obfuscated environment, it's usually "field_76578_c"
        String[] possibleFieldNames = { "biomeProvider", "field_76578_c" };
        NoSuchFieldException lastException = null;

        for (String fieldName : possibleFieldNames) {
            try {
                Field f = WorldProvider.class.getDeclaredField(fieldName);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                lastException = e; // keep trying
            }
        }

        throw lastException != null ? lastException : new NoSuchFieldException("Could not find biomeProvider field");
    }

    @SubscribeEvent
    public static void onWorldLoad(WorldEvent.Load event) {
        BiomeConfig.loadConfig(configFileBiome);
        World world = event.getWorld();

        if (world.provider instanceof WorldProviderSurface) {
            try {
                Field biomeProviderField = getBiomeProviderField();
                long actualSeed = world.getSeed();
                EarthlikeBiomeProvider customProvider = new EarthlikeBiomeProvider(actualSeed);
                biomeProviderField.set(world.provider, customProvider);
                System.out.println("Applied EarthlikeBiomeProvider with seed " + actualSeed + " to WorldProviderSurface.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
