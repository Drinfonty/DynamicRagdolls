package com.drinfonty.ragdolls.client;

import com.drinfonty.ragdolls.client.render.RagdollStateAttachment;
import com.drinfonty.ragdolls.config.RagdollsConfig;
import com.drinfonty.ragdolls.physics.RagdollPose;
import com.drinfonty.ragdolls.physics.RagdollSkeleton;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;

/**
 * Represents a live client-side ragdoll corpse with its physics skeleton and captured render state.
 */
public final class RagdollInstance {
	private final int entityId;
	private final RagdollSkeleton skeleton;
	private final HumanoidRenderState renderState;
	private final ItemStack savedHeadEquipment;
	private final ItemStack savedChestEquipment;
	private final ItemStack savedLegsEquipment;
	private final ItemStack savedFeetEquipment;
	private final long createdAtMs;
	private final long expiresAtMs;

	public RagdollInstance(
		int entityId,
		RagdollSkeleton skeleton,
		HumanoidRenderState renderState,
		long createdAtMs,
		long expiresAtMs
	) {
		this.entityId = entityId;
		this.skeleton = skeleton;
		this.renderState = renderState;
		this.savedHeadEquipment = renderState.headEquipment != null ? renderState.headEquipment.copy() : ItemStack.EMPTY;
		this.savedChestEquipment = renderState.chestEquipment != null ? renderState.chestEquipment.copy() : ItemStack.EMPTY;
		this.savedLegsEquipment = renderState.legsEquipment != null ? renderState.legsEquipment.copy() : ItemStack.EMPTY;
		this.savedFeetEquipment = renderState.feetEquipment != null ? renderState.feetEquipment.copy() : ItemStack.EMPTY;
		this.createdAtMs = createdAtMs;
		this.expiresAtMs = expiresAtMs;

		// Sanitize renderState so vanilla death tilt, nametags, and crouch offsets do not interfere
		this.renderState.deathTime = 0.0f;
		this.renderState.hasRedOverlay = false;
		this.renderState.isAutoSpinAttack = false;
		this.renderState.isUpsideDown = false;
		this.renderState.isCrouching = false;
		this.renderState.isVisuallySwimming = false;
		this.renderState.isFallFlying = false;
		this.renderState.isPassenger = false;
		this.renderState.pose = Pose.STANDING;
		this.renderState.bodyRot = 180.0f;
		this.renderState.nameTag = null;
		this.renderState.scoreText = null;
		this.renderState.leashStates = null;
		this.renderState.shadowRadius = 0.0f;
		this.renderState.shadowPieces.clear();

		if (this.renderState instanceof AvatarRenderState avatarState) {
			avatarState.isSpectator = false;
			avatarState.fallFlyingTimeInTicks = 0.0f;
			avatarState.shouldApplyFlyingYRot = false;
			avatarState.parrotOnLeftShoulder = null;
			avatarState.parrotOnRightShoulder = null;
		}
		if (this.renderState instanceof IllagerRenderState illagerState) {
			illagerState.isRiding = false;
			illagerState.armPose = AbstractIllager.IllagerArmPose.NEUTRAL;
		}
	}

	public int getEntityId() {
		return this.entityId;
	}

	public RagdollSkeleton getSkeleton() {
		return this.skeleton;
	}

	public long getCreatedAtMs() {
		return this.createdAtMs;
	}

	public boolean isExpired(long nowMs) {
		return nowMs >= this.expiresAtMs;
	}

	public HumanoidRenderState prepareRenderState(ClientLevel level, float partialTick) {
		RagdollPose pose = this.skeleton.computePose(partialTick);
		this.renderState.x = pose.rootX();
		this.renderState.y = pose.rootY();
		this.renderState.z = pose.rootZ();
		this.renderState.deathTime = 0.0f;
		this.renderState.hasRedOverlay = false;

		BlockPos lightPos = BlockPos.containing(pose.rootX(), pose.rootY() + 0.25, pose.rootZ());
		if (level != null && level.hasChunkAt(lightPos)) {
			this.renderState.lightCoords = LightCoordsUtil.pack(
				level.getBrightness(LightLayer.BLOCK, lightPos),
				level.getBrightness(LightLayer.SKY, lightPos)
			);
		}

		if (RagdollsConfig.get().renderArmorAndItems) {
			this.renderState.headEquipment = this.savedHeadEquipment;
			this.renderState.chestEquipment = this.savedChestEquipment;
			this.renderState.legsEquipment = this.savedLegsEquipment;
			this.renderState.feetEquipment = this.savedFeetEquipment;
		} else {
			this.renderState.headEquipment = ItemStack.EMPTY;
			this.renderState.chestEquipment = ItemStack.EMPTY;
			this.renderState.legsEquipment = ItemStack.EMPTY;
			this.renderState.feetEquipment = ItemStack.EMPTY;
			this.renderState.rightHandItemState.clear();
			this.renderState.leftHandItemState.clear();
		}

		if (this.renderState instanceof RagdollStateAttachment attachment) {
			attachment.ragdolls$setPose(pose);
		}
		return this.renderState;
	}
}
