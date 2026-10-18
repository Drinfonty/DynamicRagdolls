package com.drinfonty.ragdolls.client.mixin;

import com.drinfonty.ragdolls.client.render.RagdollModelPoseApplier;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelFeatureRenderer.class)
public class ModelFeatureRendererMixin {
	@Inject(
		method = "prepareModel",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V",
			shift = At.Shift.AFTER
		)
	)
	private <S> void ragdolls$afterModelSetupAnim(ModelFeatureRenderer.Submit<S> submit, CallbackInfo ci) {
		RagdollModelPoseApplier.applyIfPresent(submit.model(), submit.state());
	}
}
