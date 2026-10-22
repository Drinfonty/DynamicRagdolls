package com.drinfonty.ragdolls.client.mixin;

import com.drinfonty.ragdolls.client.render.RagdollModelPoseApplier;
import com.drinfonty.ragdolls.client.render.RagdollStateAttachment;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelFeatureRenderer.class)
public class ModelFeatureRendererMixin {
	@Inject(method = "prepareModel", at = @At("HEAD"))
	private <S> void ragdolls$beforePrepareModel(ModelFeatureRenderer.Submit<S> submit, CallbackInfo ci) {
		if (submit.state() instanceof RagdollStateAttachment attachment && attachment.ragdolls$getPose() != null) {
			RagdollModelPoseApplier.setRagdollRendering(true);
		}
	}

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

	@Inject(
		method = "prepareModel",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/model/Model;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"
		)
	)
	private <S> void ragdolls$beforeRenderToBuffer(ModelFeatureRenderer.Submit<S> submit, CallbackInfo ci) {
		RagdollModelPoseApplier.applyIfPresent(submit.model(), submit.state());
	}

	@Inject(method = "prepareModel", at = @At("RETURN"))
	private <S> void ragdolls$afterPrepareModel(ModelFeatureRenderer.Submit<S> submit, CallbackInfo ci) {
		RagdollModelPoseApplier.setRagdollRendering(false);
	}
}
