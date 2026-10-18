package com.drinfonty.ragdolls.client.mixin;

import com.drinfonty.ragdolls.client.ClientRagdollManager;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private <E extends Entity> void ragdolls$onShouldRender(
		E entity,
		Frustum frustum,
		double camX,
		double camY,
		double camZ,
		CallbackInfoReturnable<Boolean> cir
	) {
		if (entity instanceof LivingEntity living && (living.isDeadOrDying() || living.deathTime > 0)) {
			if (ClientRagdollManager.get().hasActiveRagdollForEntity(entity.getId())) {
				cir.setReturnValue(false);
			}
		}
	}
}
