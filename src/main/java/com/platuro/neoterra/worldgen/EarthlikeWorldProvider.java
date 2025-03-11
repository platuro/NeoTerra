package com.platuro.neoterra.worldgen;

import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.DimensionType;

public class EarthlikeWorldProvider extends WorldProviderSurface {

    @Override
    public void init() {
        // Replace the biome provider with our custom one before world loads
        this.biomeProvider = new EarthlikeBiomeProvider();
    }

    @Override
    public DimensionType getDimensionType() {
        return DimensionType.OVERWORLD; // Keep normal Overworld dimension type
    }

}
