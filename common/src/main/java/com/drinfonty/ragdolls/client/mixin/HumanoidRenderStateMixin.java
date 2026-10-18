package com.drinfonty.ragdolls.client.mixin;

import com.drinfonty.ragdolls.client.render.RagdollStateAttachment;
import com.drinfonty.ragdolls.physics.RagdollPose;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(HumanoidRenderState.class)
public class HumanoidRenderStateMixin implements RagdollStateAttachment {
	@Unique
	private RagdollPose ragdolls$pose;

	@Override
	public RagdollPose ragdolls$getPose() {
		return this.ragdolls$pose;
	}

	@Override
	public void ragdolls$setPose(RagdollPose pose) {
		this.ragdolls$pose = pose;
	}
}
