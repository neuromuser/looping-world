package com.neuromuser.loopingworld;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.border.WorldBorder;
import org.apache.logging.log4j.core.jmx.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LoopingWorld implements ModInitializer {
	public static final String MOD_ID = "looping-world";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static final Map<UUID, Long> playerCooldowns = new HashMap<>();
	private static final long COOLDOWN_TICKS = 10;
	@Override
	public void onInitialize() {

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			long currentTick = server.getTicks();
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				UUID playerId = player.getUuid();
				Long lastTeleport = playerCooldowns.get(playerId);

				if (lastTeleport != null && currentTick - lastTeleport < COOLDOWN_TICKS) {
					continue;
				}

				if (BorderTouchDetector.isTouchingBorder(player)) {
					TeleportPlayer(player, currentTick);
				}
			}		});
	}

	public void TeleportPlayer(ServerPlayerEntity player, long currentTick){
		WorldBorder worldBorder = player.getWorld().getWorldBorder();
		ServerWorld world = player.getServerWorld();
		double playerX = player.getX();
		double playerY = player.getY();
		double playerZ = player.getZ();


		double borderSize = worldBorder.getSize() / 2.0;
		double borderCenterX = worldBorder.getCenterX();
		double borderCenterZ = worldBorder.getCenterZ();

		double offsetX = playerX - borderCenterX;
		double offsetZ = playerZ - borderCenterZ;

		double offsetBlock = 1.0;

		double dirX = Math.signum(offsetX);
		double dirZ = Math.signum(offsetZ);

		double newX = borderCenterX - offsetX + dirX * offsetBlock;
		double newZ = borderCenterZ - offsetZ + dirZ * offsetBlock;

		double distX = Math.abs(playerX - borderCenterX);
		double distZ = Math.abs(playerZ - borderCenterZ);

		if (distX > borderSize || distZ > borderSize){
			return;
		}

		int safeY = getSafeY(world, (int)Math.floor(newX), (int)Math.floor(newZ));

		if (safeY != -100){
			double finalX = Math.floor(newX) + 0.5;
			double finalZ = Math.floor(newZ) + 0.5;

			player.teleport(world, finalX, safeY, finalZ, player.getYaw(), player.getPitch());
			player.playSound(
					SoundEvents.ENTITY_SHULKER_TELEPORT,
					SoundCategory.PLAYERS,
					1.0f,
					1.0f
			);
			player.setVelocity(0,0,0);
			player.fallDistance = 0;
			playerCooldowns.put(player.getUuid(), currentTick);
		}
		else {
			player.playSound(
					SoundEvents.ENTITY_VILLAGER_NO,
					SoundCategory.PLAYERS,
					1.0f,
					1.0f
			);
			playerCooldowns.put(player.getUuid(), currentTick);
		}

	}

	public int getSafeY(ServerWorld world, int x, int z){
		int startY = world.getRegistryKey() == net.minecraft.world.World.NETHER ?
				Math.min(120, world.getHeight() - 1) : world.getHeight() - 1;

		BlockPos.Mutable pos = new BlockPos.Mutable();

		for (int y = startY; y > world.getBottomY() + 2; y--) {
			if (isSafeLocation(world, pos, x, y, z) && isSurfaceLocation(world, pos, x, y, z)) {
				return y + 1;
			}
		}

		return -100;
	}

	private boolean isSurfaceLocation(ServerWorld world, BlockPos.Mutable pos, int x, int y, int z) {
		for (int checkY = y + 3; checkY < Math.min(y + 50, world.getHeight()); checkY++) {
			pos.set(x, checkY, z);
			BlockState state = world.getBlockState(pos);
			if (!state.isAir() && state.isSolidBlock(null, null)) {
				return false;
			}
		}
		return true;
	}

	private boolean isSafeLocation(ServerWorld world, BlockPos.Mutable pos, int x, int y, int z) {
		pos.set(x, y, z);
		BlockState ground = world.getBlockState(pos);

		pos.set(x, y + 1, z);
		BlockState feet = world.getBlockState(pos);

		pos.set(x, y + 2, z);
		BlockState head = world.getBlockState(pos);

		if (!isValidGround(ground) || !isSafePassable(feet) || !isSafePassable(head)) {
			return false;
		}

		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int dy = 0; dy <= 2; dy++) {
					pos.set(x + dx, y + dy, z + dz);
					BlockState state = world.getBlockState(pos);
					if (isDangerousBlock(state)) {
						return false;
					}
					if (dy > 0 && state.isSolidBlock(world, pos)) {
						return false;
					}
				}
			}
		}

		pos.set(x, y - 1, z);
		return !isDangerousBlock(world.getBlockState(pos));
	}

	private boolean isValidGround(BlockState state) {
		return !state.isAir() &&
				state.isSolidBlock(null, null) &&
				!isDangerousBlock(state) &&
				!state.isIn(BlockTags.SAND) &&
				!(state.getBlock() instanceof net.minecraft.block.FallingBlock);
	}

	private boolean isSafePassable(BlockState state) {
		if (state.isAir()) return true;

		if (isDangerousBlock(state) || !state.getFluidState().isEmpty() || state.isSolidBlock(null, null)) {
			return false;
		}

		return state.isIn(BlockTags.CROPS) ||
				state.isIn(BlockTags.FLOWERS) ||
				state.isIn(BlockTags.SAPLINGS) ||
				state.isIn(BlockTags.SMALL_FLOWERS) ||
				state.isIn(BlockTags.TALL_FLOWERS) ||
				state.isIn(BlockTags.REPLACEABLE) ||
				state.isIn(BlockTags.SNOW) ||
				state.isTransparent(null, null);
	}

	private boolean isDangerousBlock(BlockState state) {
		if (state.isIn(BlockTags.FIRE) || isLava(state)) {
			return true;
		}

		return state.getBlock() instanceof net.minecraft.block.MagmaBlock ||
				state.getBlock() instanceof net.minecraft.block.CampfireBlock ||
				state.getBlock() instanceof net.minecraft.block.CactusBlock ||
				state.getBlock() instanceof net.minecraft.block.SweetBerryBushBlock ||
				state.getBlock() instanceof net.minecraft.block.WitherRoseBlock ||
				state.getBlock() instanceof net.minecraft.block.PowderSnowBlock ||
				state.getBlock() instanceof net.minecraft.block.SoulFireBlock ||
				state.getBlock() instanceof net.minecraft.block.PointedDripstoneBlock ||
				state.getBlock() instanceof net.minecraft.block.AbstractFireBlock;
	}

	private boolean isLava(BlockState state) {
		return state.getFluidState().isIn(FluidTags.LAVA);
	}
}