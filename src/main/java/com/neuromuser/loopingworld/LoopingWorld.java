package com.neuromuser.loopingworld;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.tag.BlockTags;
import net.minecraft.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.border.WorldBorder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LoopingWorld implements ModInitializer {

	private static final Map<UUID, Long> cooldowns = new HashMap<>();
	private static final long COOLDOWN_TICKS = 10;

	@Override
	public void onInitialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			long tick = server.getTicks();
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				if (BorderTouchDetector.isTouchingBorder(player)) {
					teleport(player, tick);
				}
			}
		});
	}

	private void teleport(ServerPlayerEntity player, long tick) {
		UUID id = player.getUuid();
		Long last = cooldowns.get(id);
		if (last != null && tick - last < COOLDOWN_TICKS) return;

		ServerWorld world = player.getWorld();
		WorldBorder border = world.getWorldBorder();

		double x = player.getX();
		double z = player.getZ();

		double half = border.getSize() / 2.0;
		double cx = border.getCenterX();
		double cz = border.getCenterZ();

		double ox = x - cx;
		double oz = z - cz;

		if (Math.abs(ox) > half || Math.abs(oz) > half) return;

		double nx = cx - ox + Math.signum(ox);
		double nz = cz - oz + Math.signum(oz);

		int bx = (int) Math.floor(nx);
		int bz = (int) Math.floor(nz);

		int y = getSafeSurfaceY(world, bx, bz);
		if (y <= world.getBottomY()) {
			play(player, net.minecraft.sound.SoundEvents.ENTITY_VILLAGER_NO, x, player.getY(), z);
			cooldowns.put(id, tick);
			return;
		}

		double fx = bx + 0.5;
		double fz = bz + 0.5;

		play(player, net.minecraft.sound.SoundEvents.ENTITY_SHULKER_TELEPORT, x, player.getY(), z);
		player.teleport(world, fx, y, fz, player.getYaw(), player.getPitch());
		play(player, net.minecraft.sound.SoundEvents.ENTITY_SHULKER_TELEPORT, fx, y, fz);

		player.setVelocity(0, 0, 0);
		player.fallDistance = 0;
		cooldowns.put(id, tick);
	}

	private int getSafeSurfaceY(ServerWorld world, int x, int z) {
		int surface = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
		BlockPos groundPos = new BlockPos(x, surface - 1, z);
		BlockPos feetPos = new BlockPos(x, surface, z);
		BlockPos headPos = feetPos.up();

		if (surface <= world.getBottomY()) return world.getBottomY() - 1;

		BlockState ground = world.getBlockState(groundPos);
		BlockState feet = world.getBlockState(feetPos);
		BlockState head = world.getBlockState(headPos);

		if (isHazardous(ground)) return world.getBottomY() - 1;
		if (!isClear(feet, world, feetPos)) return world.getBottomY() - 1;
		if (!isClear(head, world, headPos)) return world.getBottomY() - 1;

		return surface;
	}

	private boolean isClear(BlockState state, ServerWorld world, BlockPos pos) {
		if (state.isAir()) return true;
		if (!state.getFluidState().isEmpty()) return false;
		return state.getCollisionShape(world, pos).isEmpty();
	}

	private boolean isHazardous(BlockState state) {
		if (state.getFluidState().isIn(FluidTags.LAVA)) return true;
		if (state.isIn(BlockTags.FIRE)) return true;

		return state.getBlock() == Blocks.MAGMA_BLOCK ||
				state.getBlock() == Blocks.CAMPFIRE ||
				state.getBlock() == Blocks.SOUL_CAMPFIRE ||
				state.getBlock() == Blocks.CACTUS ||
				state.getBlock() == Blocks.SWEET_BERRY_BUSH ||
				state.getBlock() == Blocks.WITHER_ROSE ||
				state.getBlock() == Blocks.POWDER_SNOW;
	}

	private void play(ServerPlayerEntity player, SoundEvent sound, double x, double y, double z) {
		player.networkHandler.sendPacket(new PlaySoundS2CPacket(
				sound,
				SoundCategory.PLAYERS,
				x, y, z,
				1.0f,
				1.0f,
				player.getWorld().random.nextLong()
		));
	}
}
