package com.platuro.neoterra.worldgen;

import com.platuro.neoterra.config.BiomeConfig;
import com.platuro.neoterra.manager.SeasonalSkyManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.DimensionType;
import net.minecraft.world.biome.BiomeProvider;

public class EarthlikeWorldProvider extends WorldProviderSurface {
    private EarthlikeBiomeProvider customProvider;

    @Override
    public void init() {
        super.init();
        // Create your custom biome provider once
        this.customProvider = new EarthlikeBiomeProvider(this.world.getSeed());
        this.biomeProvider = customProvider;
    }

    @Override
    public DimensionType getDimensionType() {
        return DimensionType.OVERWORLD;
    }

    @Override
    public boolean isSurfaceWorld() {
        return true;
    }

    @Override
    public float calculateCelestialAngle(long worldTime, float partialTicks) {
        // 1) Get the *client* player. In 1.12 or earlier, you can do:
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null) {
            // fallback if no player loaded (e.g. server tick?)
            return super.calculateCelestialAngle(worldTime, partialTicks);
        }

        // 2) Get the player's X/Z (or BlockPos)
        double x = player.posX;
        double z = player.posZ;

        // For example, compute latVal as absolute Z normalized somehow:
        float baseLatVal = (float)Math.abs(z) / BiomeConfig.MAX_WORLD_HEIGHT;

        // 3) Maybe also compute 'seasonVal' from your season system?
        float seasonVal = 1.0f;

        // 4) Then call your custom method to get the angle
        return SeasonalSkyManager.getCelestialAngle(worldTime, partialTicks, baseLatVal, seasonVal);
    }


}
