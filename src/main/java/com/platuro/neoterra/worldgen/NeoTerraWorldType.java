package com.platuro.neoterra.worldgen;

import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.gen.IChunkGenerator;
import com.platuro.neoterra.worldgen.NeoTerraChunkGenerator;

public class NeoTerraWorldType extends WorldType {
    public NeoTerraWorldType() {
        super("neoterra");
    }

    @Override
    public IChunkGenerator getChunkGenerator(World world, String generatorOptions) {
        return new NeoTerraChunkGenerator(world);
    }
}
