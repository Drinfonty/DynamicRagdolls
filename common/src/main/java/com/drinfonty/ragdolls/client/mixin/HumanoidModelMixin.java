package com.drinfonty.ragdolls.client.mixin;

import com.drinfonty.ragdolls.client.render.RagdollModelPoseApplier;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public class HumanoidModelMixin<T extends HumanoidRenderState> {
	@Inject(
		method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V",
		at = @At("TAIL")
	)
	private void ragdolls$onSetupAnim(T state, CallbackInfo ci) {
		RagdollModelPoseApplier.applyIfPresent(this, state);
	}
}
