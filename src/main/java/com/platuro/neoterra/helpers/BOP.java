package com.platuro.neoterra.helpers;

import net.minecraft.world.biome.Biome;

import java.lang.reflect.Field;

public class BOP {
    public static Biome getBOPBiome(String fieldName) {
        try {
            Class<?> bopClass = Class.forName("biomesoplenty.api.biome.BOPBiomes");
            try {
                Field f = bopClass.getField(fieldName);
                Object obj = f.get(null); // static field e.g. BOPBiomes.alps
                // The field is an Optional<Biome>
                if (obj instanceof com.google.common.base.Optional) {
                    com.google.common.base.Optional<?> optionalBiome = (com.google.common.base.Optional<?>) obj;
                    if (optionalBiome.isPresent() && optionalBiome.get() instanceof Biome) {
                        return (Biome) optionalBiome.get();
                    }
                }
            } catch (NoSuchFieldException e) {
                System.out.println("No such BOP field: " + fieldName);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
