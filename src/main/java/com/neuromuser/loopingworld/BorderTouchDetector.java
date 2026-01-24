package com.neuromuser.loopingworld;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.border.WorldBorder;

public class BorderTouchDetector {

    public static boolean isTouchingBorder(PlayerEntity player){
        WorldBorder worldBorder = player.getWorld().getWorldBorder();

        double distance = worldBorder.getDistanceInsideBorder(player);
        if (distance <= 0.31) {
            return true;
        } else {
            return false;
        }
    }
}
