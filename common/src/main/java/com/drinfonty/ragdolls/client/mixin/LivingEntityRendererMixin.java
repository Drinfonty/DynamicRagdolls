package com.drinfonty.ragdolls.client.mixin;

import com.drinfonty.ragdolls.client.render.RagdollModelPoseApplier;
import com.drinfonty.ragdolls.client.render.RagdollStateAttachment;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin<S extends LivingEntityRenderState, M extends EntityModel<? super S>> {
	@Shadow
	protected M model;

	@Inject(
		method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void ragdolls$skipVanillaRotations(
		S state,
		PoseStack poseStack,
		float bodyRot,
		float scale,
		CallbackInfo ci
	) {
		if (state instanceof RagdollStateAttachment attachment && attachment.ragdolls$getPose() != null) {
			ci.cancel();
		}
	}

	@Inject(
		method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At("HEAD")
	)
	private void ragdolls$beforeSubmit(
		S state,
		PoseStack poseStack,
		SubmitNodeCollector collector,
		CameraRenderState cameraRenderState,
		CallbackInfo ci
	) {
		if (state instanceof RagdollStateAttachment attachment && attachment.ragdolls$getPose() != null) {
			RagdollModelPoseApplier.setRagdollRendering(true);
		}
	}

	@Inject(
		method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Ljava/lang/Object;)V",
			shift = At.Shift.AFTER
		)
	)
	private void ragdolls$applyPoseBeforeLayers(
		S state,
		PoseStack poseStack,
		SubmitNodeCollector collector,
		CameraRenderState cameraRenderState,
		CallbackInfo ci
	) {
		RagdollModelPoseApplier.applyIfPresent(this.model, state);
	}

	@Inject(
		method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At("RETURN")
	)
	private void ragdolls$afterSubmit(
		S state,
		PoseStack poseStack,
		SubmitNodeCollector collector,
		CameraRenderState cameraRenderState,
		CallbackInfo ci
	) {
		RagdollModelPoseApplier.setRagdollRendering(false);
	}
}
