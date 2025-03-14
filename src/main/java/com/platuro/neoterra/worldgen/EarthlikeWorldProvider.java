package com.platuro.neoterra.worldgen;

import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.DimensionType;
import net.minecraft.world.biome.BiomeProvider;

public class EarthlikeWorldProvider extends WorldProviderSurface {
    private EarthlikeBiomeProvider customProvider;

    @Override
    public void init() {
        // Create your custom biome provider once
        this.customProvider = new EarthlikeBiomeProvider(this.world.getSeed());
        this.biomeProvider = customProvider;
    }

    @Override
    public DimensionType getDimensionType() {
        return DimensionType.OVERWORLD;
    }

    @Override
    public BiomeProvider getBiomeProvider() {
        return this.customProvider;
    }
}
