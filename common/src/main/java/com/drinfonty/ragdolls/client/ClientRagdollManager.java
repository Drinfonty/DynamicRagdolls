package com.drinfonty.ragdolls.client;

import com.drinfonty.ragdolls.RagdollsMod;
import com.drinfonty.ragdolls.config.RagdollsConfig;
import com.drinfonty.ragdolls.physics.RagdollSkeleton;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.phys.Vec3;

/**
 * Manages active client-side ragdolls, death impulse computation, physics ticking, and expiration.
 */
public final class ClientRagdollManager {
	private static final ClientRagdollManager INSTANCE = new ClientRagdollManager();

	private final Map<Integer, RagdollInstance> ragdollsByEntityId = new LinkedHashMap<>();

	private ClientRagdollManager() {
	}

	public static ClientRagdollManager get() {
		return INSTANCE;
	}

	public synchronized boolean hasActiveRagdollForEntity(int entityId) {
		return this.ragdollsByEntityId.containsKey(entityId);
	}

	public synchronized Collection<RagdollInstance> getActiveRagdolls() {
		return java.util.List.copyOf(this.ragdollsByEntityId.values());
	}

	public synchronized int getActiveCount() {
		return this.ragdollsByEntityId.size();
	}

	public synchronized void clearAll() {
		this.ragdollsByEntityId.clear();
	}

	public synchronized void clearChunk(int chunkX, int chunkZ) {
		Iterator<Map.Entry<Integer, RagdollInstance>> it = this.ragdollsByEntityId.entrySet().iterator();
		while (it.hasNext()) {
			RagdollInstance instance = it.next().getValue();
			int rx = Mth.floor(instance.getSkeleton().getRootX()) >> 4;
			int rz = Mth.floor(instance.getSkeleton().getRootZ()) >> 4;
			if (rx == chunkX && rz == chunkZ) {
				it.remove();
			}
		}
	}

	/**
	 * Attempts to spawn a physics ragdoll for the given dying entity on the client.
	 */
	public synchronized void onEntityDeath(LivingEntity entity, DamageSource damageSource) {
		RagdollsConfig config = RagdollsConfig.get();
		if (!config.ragdollsEnabled || entity == null || !entity.level().isClientSide() || entity instanceof ArmorStand) {
			return;
		}
		if (this.ragdollsByEntityId.containsKey(entity.getId())) {
			return;
		}

		boolean isPlayerOrAvatar = (entity instanceof Player) || (entity instanceof Avatar);
		if (!isPlayerOrAvatar && !config.isHumanoidsEnabled()) {
			return;
		}

		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.getEntityRenderDispatcher() == null) {
			return;
		}

		EntityRenderState extracted;
		try {
			extracted = mc.getEntityRenderDispatcher().extractEntity(entity, 1.0f);
		} catch (Throwable t) {
			if (RagdollsMod.DEBUG) {
				RagdollsMod.LOGGER.warn("Failed to extract render state for ragdoll on entity {}", entity, t);
			}
			return;
		}

		if (!(extracted instanceof HumanoidRenderState humanoidState)) {
			return;
		}

		double baseModelScale = (humanoidState instanceof AvatarRenderState) ? 0.9375 : 1.0;
		double totalRenderScale = baseModelScale * humanoidState.scale * (humanoidState.isBaby ? 0.5 : 1.0);

		Vec3 vel = entity.getDeltaMovement();
		double[] impulse = computeDeathImpulse(entity, damageSource, config.knockbackMultiplier);

		RagdollSkeleton skeleton = new RagdollSkeleton(
			entity.getX(),
			entity.getY(),
			entity.getZ(),
			entity.yBodyRot,
			entity.getXRot(),
			totalRenderScale,
			vel.x,
			vel.y,
			vel.z,
			impulse[0],
			impulse[1],
			impulse[2]
		);

		long now = System.currentTimeMillis();
		long lifetimeMs = Math.max(1, config.ragdollLifetimeSeconds) * 1000L;
		RagdollInstance instance = new RagdollInstance(
			entity.getId(),
			skeleton,
			humanoidState,
			now,
			now + lifetimeMs
		);

		// Enforce maxActiveRagdolls cap (evict oldest first)
		while (this.ragdollsByEntityId.size() >= config.maxActiveRagdolls && !this.ragdollsByEntityId.isEmpty()) {
			Iterator<Integer> it = this.ragdollsByEntityId.keySet().iterator();
			if (it.hasNext()) {
				it.next();
				it.remove();
			}
		}

		this.ragdollsByEntityId.put(entity.getId(), instance);
		if (RagdollsMod.DEBUG) {
			RagdollsMod.LOGGER.info(
				"Spawned ragdoll for entity {} (id={}, scale={}, impulse=[{}, {}, {}])",
				entity.getType().getDescriptionId(), entity.getId(), totalRenderScale,
				impulse[0], impulse[1], impulse[2]
			);
		}
	}

	public synchronized void tick(ClientLevel level, long nowMs) {
		if (level == null) {
			return;
		}
		RagdollsConfig config = RagdollsConfig.get();
		if (!config.ragdollsEnabled) {
			this.ragdollsByEntityId.clear();
			return;
		}

		ClientRagdollWorld ragdollWorld = new ClientRagdollWorld(level);
		float gravity = config.gravityScale;
		float bounciness = config.bounciness;
		float friction = config.groundFriction;
		boolean buoyancy = config.waterBuoyancy;
		float coneScale = config.getJointConeScale();
		int solverIterations = config.getSolverIterations();
		boolean groundDust = config.groundDustParticles;
		boolean hitSounds = config.collisionSounds;

		Iterator<Map.Entry<Integer, RagdollInstance>> it = this.ragdollsByEntityId.entrySet().iterator();
		while (it.hasNext()) {
			RagdollInstance instance = it.next().getValue();
			if (instance.isExpired(nowMs)) {
				it.remove();
				continue;
			}
			instance.getSkeleton().step(
				ragdollWorld,
				gravity,
				bounciness,
				friction,
				buoyancy,
				coneScale,
				solverIterations,
				groundDust,
				hitSounds
			);
		}
	}

	private static double[] computeDeathImpulse(LivingEntity entity, DamageSource damageSource, float configMultiplier) {
		Vec3 attackerPos = null;
		boolean isExplosion = false;
		if (damageSource != null) {
			if (damageSource.is(DamageTypeTags.IS_EXPLOSION)) {
				isExplosion = true;
			}
			if (damageSource.getDirectEntity() != null) {
				attackerPos = damageSource.getDirectEntity().position();
			} else if (damageSource.getEntity() != null) {
				attackerPos = damageSource.getEntity().position();
			} else {
				attackerPos = damageSource.getSourcePosition();
			}
		}

		Minecraft mc = Minecraft.getInstance();
		if (attackerPos == null && mc != null && mc.player != null && mc.player != entity) {
			if (mc.player.distanceToSqr(entity) <= 36.0) {
				attackerPos = mc.player.position();
			}
		}

		float weaponScale = getWeaponScale(entity, damageSource, isExplosion);
		double totalScale = configMultiplier * weaponScale;

		double dirX = 0.0;
		double dirZ = 0.0;
		if (attackerPos != null) {
			double dx = entity.getX() - attackerPos.x();
			double dz = entity.getZ() - attackerPos.z();
			double lenSq = dx * dx + dz * dz;
			if (lenSq > 1.0e-5) {
				double len = Math.sqrt(lenSq);
				dirX = dx / len;
				dirZ = dz / len;
			}
		}

		if (dirX == 0.0 && dirZ == 0.0) {
			// Fall backward relative to entity's facing direction
			double yawRad = Math.toRadians(entity.yBodyRot);
			dirX = Math.sin(yawRad);
			dirZ = -Math.cos(yawRad);
		}

		double horizontalSpeed = (isExplosion ? 0.42 : 0.22) * totalScale;
		double upwardSpeed = (isExplosion ? 0.28 : 0.10) * totalScale;
		return new double[]{
			dirX * horizontalSpeed,
			upwardSpeed,
			dirZ * horizontalSpeed
		};
	}

	private static float getWeaponScale(LivingEntity entity, DamageSource damageSource, boolean isExplosion) {
		if (isExplosion) {
			return 2.2f;
		}
		LivingEntity attacker = null;
		if (damageSource != null && damageSource.getEntity() instanceof LivingEntity livingAttacker) {
			attacker = livingAttacker;
		} else {
			Minecraft mc = Minecraft.getInstance();
			if (mc != null && mc.player != null && mc.player != entity && mc.player.distanceToSqr(entity) <= 36.0) {
				attacker = mc.player;
			}
		}
		if (attacker == null) {
			return 1.0f;
		}

		ItemStack stack = attacker.getMainHandItem();
		if (stack.isEmpty()) {
			return 0.7f;
		}
		if (stack.getItem() instanceof MaceItem) {
			return 2.0f;
		}
		if (stack.is(ItemTags.AXES)) {
			return 1.5f;
		}
		if (stack.is(ItemTags.SWORDS)) {
			return 1.15f;
		}
		if (stack.getItem() instanceof ProjectileWeaponItem) {
			return 0.9f;
		}
		return 1.0f;
	}
}
