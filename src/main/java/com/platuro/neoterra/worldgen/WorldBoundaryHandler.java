package com.platuro.neoterra.worldgen;

import com.platuro.neoterra.config.BiomeConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketEntityTeleport;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.init.SoundEvents;
import net.minecraft.world.WorldServer;

public class WorldBoundaryHandler {

    private static final int MAX_PLANET_WIDTH = (int) (BiomeConfig.MAX_WORLD_WIDTH - 200);
    private static final int POLAR_Z_LIMIT = (int) (BiomeConfig.MAX_WORLD_HEIGHT - (BiomeConfig.MAX_WORLD_HEIGHT * 0.2));

    public static void handlePlayerTeleportation(EntityPlayer player) {
        if (player.world.isRemote) return; // Server-side only

        Entity entity = player.isRiding() ? player.getRidingEntity() : player;

        double x = entity.posX;
        double z = entity.posZ;
        double y = entity.posY;

        boolean teleported = false;

        // Handle X-axis teleportation
        if (x >= MAX_PLANET_WIDTH) {
            x = -MAX_PLANET_WIDTH + 5;
            teleported = true;
        } else if (x <= -MAX_PLANET_WIDTH) {
            x = MAX_PLANET_WIDTH - 5;
            teleported = true;
        }

        // Handle Z-axis teleportation (polar teleportation)
        if (z >= POLAR_Z_LIMIT) {
            z = -POLAR_Z_LIMIT + 5;
            teleported = true;
        } else if (z <= -POLAR_Z_LIMIT) {
            z = POLAR_Z_LIMIT - 5;
            teleported = true;
        }

        if (teleported) {
            teleportEntitySafely(entity, x, y, z);
            teleportEffects(player);
        }
    }

    private static void teleportEntitySafely(Entity entity, double x, double y, double z) {
        if (entity instanceof EntityPlayerMP) {
            EntityPlayerMP playerMP = (EntityPlayerMP) entity;

            if (playerMP.isRiding()) {
                Entity riding = playerMP.getRidingEntity();
                playerMP.dismountRidingEntity();
                riding.setPositionAndUpdate(x, y, z);
                playerMP.connection.setPlayerLocation(x, y, z, playerMP.rotationYaw, playerMP.rotationPitch);
                playerMP.startRiding(riding, true);
            } else {
                playerMP.connection.setPlayerLocation(x, y, z, playerMP.rotationYaw, playerMP.rotationPitch);
            }
        } else {
            entity.setPositionAndUpdate(x, y, z);
            SPacketEntityTeleport packet = new SPacketEntityTeleport(entity);
            ((WorldServer) entity.world).getEntityTracker().sendToTracking(entity, packet);
        }
    }

    private static void teleportEffects(EntityPlayer player) {
        player.world.playSound(null, player.getPosition(),
                SoundEvents.ENTITY_ENDERMEN_TELEPORT,
                SoundCategory.PLAYERS, 1.0F, 1.0F);

        ((WorldServer) player.world).spawnParticle(EnumParticleTypes.PORTAL,
                player.posX, player.posY + 1, player.posZ,
                32, 0.5D, 1.0D, 0.5D, 0.1D);
    }
}
