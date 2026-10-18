package com.drinfonty.ragdolls.client.test;

import com.drinfonty.ragdolls.RagdollsMod;
import com.drinfonty.ragdolls.client.ClientRagdollManager;
import com.drinfonty.ragdolls.client.RagdollInstance;
import com.drinfonty.ragdolls.config.RagdollsConfig;
import com.drinfonty.ragdolls.physics.RagdollPose;
import com.drinfonty.ragdolls.physics.RagdollSkeleton;
import java.io.File;
import java.util.Collection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * Headless in-game automated test runner active only when -Dragdolls.smokeTest=true is passed.
 * Builds an on-the-fly arena, summons a test humanoid / mannequin, kills it, and verifies ragdoll physics.
 */
public final class InGameSmokeTest {
	public static final boolean ENABLED = Boolean.getBoolean("ragdolls.smokeTest")
			|| "true".equalsIgnoreCase(System.getenv("RAGDOLLS_SMOKE_TEST"));
	private static volatile boolean playerSpawned = false;
	private static int state = 0;
	private static int tickCounter = 0;
	private static BlockPos testOrigin = null;
	private static LivingEntity targetEntity = null;
	private static float targetYaw = 0.0f;
	private static float targetPitch = 22.0f;

	private InGameSmokeTest() {
	}

	public static void onPlayerSpawned() {
		if (!playerSpawned) {
			playerSpawned = true;
			RagdollsMod.LOGGER.info("[SmokeTest] Intercepted player spawn event: player joined client level!");
		}
	}

	public static void tick(Minecraft client) {
		if (!ENABLED || state == 99) {
			return;
		}

		if (state == 0) {
			tickCounter++;
			Object activeScreen = getActiveScreen(client);
			if (activeScreen != null && tickCounter % 20 == 0) {
				RagdollsMod.LOGGER.info("[SmokeTest] Waiting for player spawn... Active screen: " + activeScreen.getClass().getName());
			}
			if (!playerSpawned || activeScreen != null || client.player == null || client.level == null) {
				return;
			}

			state = 1;
			RagdollsMod.LOGGER.info("=================================================");
			RagdollsMod.LOGGER.info("Player spawned & in gameplay. Starting Ragdolls Smoke Test Suite");
			RagdollsMod.LOGGER.info("=================================================");

			try {
				RagdollsConfig.get().ragdollsEnabled = true;
				RagdollsConfig.get().entityScope = "Humanoids";
				BlockPos origin = client.player.blockPosition().relative(client.player.getDirection(), 3);
				testOrigin = origin;
				setupArenaAndTarget(client, origin);
				state = 2;
				tickCounter = 0;
			} catch (Throwable t) {
				fail(t);
			}
			return;
		}

		if (state == 2) {
			tickCounter++;
			if (targetEntity == null) {
				AABB box = new AABB(testOrigin).inflate(4.0);
				for (Entity entity : client.level.entitiesForRendering()) {
					if (entity instanceof LivingEntity living && living.isAlive() && !(living instanceof Player)) {
						if (box.contains(living.position())) {
							targetEntity = living;
							break;
						}
					}
				}
			}

			if (targetEntity == null) {
				if (tickCounter > 40) {
					fail(new AssertionError("Timeout waiting for summoned test humanoid to appear on client!"));
				}
				return;
			}

			try {
				double dx = targetEntity.getX() - client.player.getX();
				double dy = (targetEntity.getY() + 0.5) - client.player.getEyeY();
				double dz = targetEntity.getZ() - client.player.getZ();
				double dist = Math.sqrt(dx * dx + dz * dz);
				targetYaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
				targetPitch = (float) (-Math.atan2(dy, dist) * 180.0 / Math.PI);
				client.player.setYRot(targetYaw);
				client.player.setXRot(targetPitch);

				ClientRagdollManager.get().clearAll();

				// Kill the summoned target entity via server command
				var server = client.getSingleplayerServer();
				if (server != null) {
					var source = server.createCommandSourceStack();
					server.getCommands().performPrefixedCommand(source, "kill @e[tag=ragdolls_test_target]");
				}

				state = 3;
				tickCounter = 0;
			} catch (Throwable t) {
				fail(t);
			}
			return;
		}

		if (state == 3) {
			client.player.setYRot(targetYaw);
			client.player.setXRot(targetPitch);
			tickCounter++;

			Collection<RagdollInstance> active = ClientRagdollManager.get().getActiveRagdolls();
			if (active.isEmpty()) {
				if (tickCounter > 40) {
					fail(new AssertionError("Killing target entity did not spawn any active RagdollInstance!"));
				}
				return;
			}

			// Allow 25 ticks for the ragdoll to topple and collide with the ground
			if (tickCounter < 25) {
				return;
			}

			RagdollInstance instance = active.iterator().next();
			RagdollSkeleton skeleton = instance.getSkeleton();
			RagdollPose pose = skeleton.computePose(1.0f);
			if (pose.head() == null || pose.body() == null || pose.leftArm() == null || pose.rightArm() == null) {
				fail(new AssertionError("RagdollPose returned null limb transform!"));
				return;
			}

			double neckY = skeleton.getParticle(RagdollSkeleton.NECK).y;
			double footY = skeleton.getParticle(RagdollSkeleton.LEFT_FOOT).y;
			RagdollsMod.LOGGER.info("[SmokeTest] Ragdoll active after 25 ticks (neckY={}, footY={})", neckY, footY);

			RagdollsMod.LOGGER.info("=================================================");
			RagdollsMod.LOGGER.info("VERIFIED: Ragdoll spawned, simulated, and persisted after entity death!");
			RagdollsMod.LOGGER.info("ALL RAGDOLLS IN-GAME SMOKE TESTS PASSED CLEANLY!");
			RagdollsMod.LOGGER.info("=================================================");

			captureScreenshot(client);
			state = 99;

			new Thread(() -> {
				try {
					Thread.sleep(1500);
					client.execute(client::stop);
				} catch (Exception ignored) {
				}
			}, "Ragdolls-SmokeTest-Shutdown").start();
		}
	}

	private static void setupArenaAndTarget(Minecraft client, BlockPos origin) {
		var server = client.getSingleplayerServer();
		if (server != null) {
			var commands = server.getCommands();
			var source = server.createCommandSourceStack();
			int floorY = client.player.getBlockY() - 1;
			int ox = origin.getX();
			int oz = origin.getZ();
			Direction forward = client.player.getDirection();
			BlockPos standPos = new BlockPos(ox, floorY, oz).relative(forward.getOpposite(), 3);

			commands.performPrefixedCommand(source, "difficulty normal");
			commands.performPrefixedCommand(source, "gamerule doMobSpawning false");
			commands.performPrefixedCommand(source, String.format(java.util.Locale.ROOT,
				"fill %d %d %d %d %d %d air", ox - 4, floorY, oz - 4, ox + 4, floorY + 5, oz + 4));
			commands.performPrefixedCommand(source, String.format(java.util.Locale.ROOT,
				"fill %d %d %d %d %d %d smooth_stone", ox - 4, floorY - 1, oz - 4, ox + 4, floorY - 1, oz + 4));
			commands.performPrefixedCommand(source, String.format(java.util.Locale.ROOT,
				"tp @p %.2f %.2f %.2f", standPos.getX() + 0.5, (double) floorY, standPos.getZ() + 0.5));
			commands.performPrefixedCommand(source, String.format(java.util.Locale.ROOT,
				"summon husk %d %d %d {NoAI:1b,Silent:1b,Tags:[\"ragdolls_test_target\"]}", ox, floorY, oz));
		}
	}

	private static Object getActiveScreen(Minecraft client) {
		try {
			if (client.gui != null) {
				try {
					java.lang.reflect.Method screenMethod = client.gui.getClass().getMethod("screen");
					Object screen = screenMethod.invoke(client.gui);
					if (screen != null) {
						return screen;
					}
				} catch (NoSuchMethodException ignored) {
				}
			}
			java.lang.reflect.Field screenField = Minecraft.class.getField("screen");
			return screenField.get(client);
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static void captureScreenshot(Minecraft client) {
		try {
			java.lang.reflect.Method m = Screenshot.class.getMethod("grab", Minecraft.class, boolean.class);
			m.invoke(null, client, false);
		} catch (Throwable ignored) {
		}
	}

	private static void fail(Throwable t) {
		RagdollsMod.LOGGER.error("RAGDOLLS IN-GAME SMOKE TEST FAILED!", t);
		System.err.println("FATAL: RAGDOLLS IN-GAME SMOKE TEST FAILED!");
		t.printStackTrace(System.err);
		System.exit(1);
	}
}
