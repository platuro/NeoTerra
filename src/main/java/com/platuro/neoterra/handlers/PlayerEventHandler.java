package com.platuro.neoterra.handlers;

import com.platuro.neoterra.worldgen.WorldBoundaryHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayer;

public class PlayerEventHandler {

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            EntityPlayer player = event.player;
            WorldBoundaryHandler.handlePlayerTeleportation(player);
        }
    }
}
