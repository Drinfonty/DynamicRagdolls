package com.drinfonty.ragdolls.client.mixin.compat;

import com.drinfonty.ragdolls.client.render.RagdollModelPoseApplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts EMF's animation pause checks so EMF does not evaluate custom animations
 * while an entity's ragdoll pose is being rendered.
 */
@Pseudo
@Mixin(targets = "traben.entity_model_features.models.animation.EMFAnimationEntityContext", remap = false)
public class EMFAnimationEntityContextMixin {
	@Inject(method = "isEntityAnimPaused", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
	private static void ragdolls$pauseEMFForRagdolls(CallbackInfoReturnable<Boolean> cir) {
		if (RagdollModelPoseApplier.isRagdollRendering()) {
			cir.setReturnValue(true);
		}
	}
}
